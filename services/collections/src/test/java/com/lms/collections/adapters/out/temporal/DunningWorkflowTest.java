package com.lms.collections.adapters.out.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.collections.adapters.out.temporal.DunningCommands.DpdTick;
import com.lms.collections.adapters.out.temporal.DunningCommands.PromiseToPay;
import com.lms.collections.adapters.out.temporal.DunningCommands.StartDunningCommand;
import com.lms.collections.adapters.out.temporal.DunningCommands.StepDef;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the dunning workflow in the in-JVM, time-skipping {@link TestWorkflowEnvironment} (ADR
 * 0008/0011): steps fire as DPD ticks advance, duplicate/replayed ticks never repeat an action, a
 * promise to pay pauses reminders and a broken promise resumes them, and a payment that clears the
 * overdue amount cancels the remaining steps. The test advances the (skipped) clock a little
 * between signals so each lands on its own workflow task, exactly as day-separated ticks would in
 * production. No Docker, no real broker or DB.
 */
class DunningWorkflowTest {

    private static final String TASK_QUEUE = "collections";

    private TestWorkflowEnvironment env;
    private FakeDunningActivities activities;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        activities = new FakeDunningActivities();
        Worker worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(DunningWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private static List<StepDef> defaultSteps() {
        return List.of(
                new StepDef("DPD_1_SMS", 1, "SMS", "SMS", "d.dpd1"),
                new StepDef("DPD_3_SMS_EMAIL", 3, "SMS_EMAIL", "SMS", "d.dpd3"),
                new StepDef("DPD_7_SMS_REPRESENT", 7, "SMS_REPRESENT", "SMS", "d.dpd7"),
                new StepDef("DPD_15_ESCALATE", 15, "ESCALATE", "NONE", ""),
                new StepDef("DPD_30_FORMAL", 30, "FORMAL_NOTICE", "EMAIL", "d.dpd30"),
                new StepDef("DPD_60_FINAL", 60, "FINAL_NOTICE", "EMAIL", "d.dpd60"),
                new StepDef("DPD_91_NPA", 91, "NPA_REVIEW", "NONE", ""),
                new StepDef("DPD_180_WRITE_OFF", 180, "RECOMMEND_WRITE_OFF", "NONE", ""));
    }

    private DunningWorkflow start(String loanId) {
        DunningWorkflow wf =
                env.getWorkflowClient()
                        .newWorkflowStub(
                                DunningWorkflow.class,
                                WorkflowOptions.newBuilder()
                                        .setTaskQueue(TASK_QUEUE)
                                        .setWorkflowId("dun-" + loanId)
                                        .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                                        .build());
        WorkflowClient.start(
                wf::run,
                new StartDunningCommand(
                        "case-" + loanId, loanId, "LN-" + loanId, 1, defaultSteps()));
        return wf;
    }

    /** Let the workflow process the signals delivered so far (the test clock is skipped). */
    private void settle() {
        env.sleep(Duration.ofSeconds(1));
    }

    private static long epochDay(int y, int m, int d) {
        return LocalDate.of(y, m, d).toEpochDay();
    }

    private static void awaitDone(DunningWorkflow wf) {
        WorkflowStub.fromTyped(wf).getResult(Void.class);
    }

    @Test
    void runsDpd1Through7ThenResolvesOnClearedPayment() {
        DunningWorkflow wf = start("B");
        wf.onDpdTick(new DpdTick(1, "DPD_1_30", epochDay(2026, 11, 14)));
        settle();
        wf.onDpdTick(new DpdTick(3, "DPD_1_30", epochDay(2026, 11, 16)));
        settle();
        wf.onDpdTick(new DpdTick(7, "DPD_1_30", epochDay(2026, 11, 20)));
        settle();
        // Payment clears the overdue amount: remaining steps are cancelled and the case resolves.
        wf.onPaymentReceived(true);
        awaitDone(wf);

        assertThat(activities.firedStepIds)
                .containsExactly("DPD_1_SMS", "DPD_3_SMS_EMAIL", "DPD_7_SMS_REPRESENT");
        assertThat(activities.closedStatus).isEqualTo("RESOLVED");
        assertThat(wf.currentStatus()).isEqualTo("RESOLVED");
    }

    @Test
    void duplicateAndReplayedTicksNeverRepeatAnAction() {
        DunningWorkflow wf = start("DUP");
        wf.onDpdTick(new DpdTick(7, "DPD_1_30", epochDay(2026, 11, 20)));
        settle();
        // Same and lower DPD delivered again (duplicate/out-of-order redeliveries).
        wf.onDpdTick(new DpdTick(7, "DPD_1_30", epochDay(2026, 11, 20)));
        settle();
        wf.onDpdTick(new DpdTick(3, "DPD_1_30", epochDay(2026, 11, 16)));
        settle();
        wf.onDpdTick(new DpdTick(0, "CURRENT", epochDay(2026, 11, 25)));
        awaitDone(wf);

        assertThat(activities.firedStepIds)
                .containsExactly("DPD_1_SMS", "DPD_3_SMS_EMAIL", "DPD_7_SMS_REPRESENT");
        assertThat(activities.closedStatus).isEqualTo("RESOLVED");
    }

    @Test
    void promiseToPayPausesRemindersAndABrokenPromiseResumes() {
        DunningWorkflow wf = start("PROMISE");
        wf.onDpdTick(new DpdTick(3, "DPD_1_30", epochDay(2026, 11, 16)));
        settle();
        // Promise to pay by 2026-11-25: later ticks before that date fire no new step.
        wf.onPromiseToPay(new PromiseToPay(epochDay(2026, 11, 25), 474_000L, "INR"));
        settle();
        wf.onDpdTick(new DpdTick(7, "DPD_1_30", epochDay(2026, 11, 20)));
        settle();
        // Promised date reached while still overdue: promise broken, reminders resume.
        wf.onDpdTick(new DpdTick(10, "DPD_1_30", epochDay(2026, 11, 26)));
        settle();
        wf.onPaymentReceived(true);
        awaitDone(wf);

        // DPD_7 (paused during the promise) fires only after the promise breaks, before resolution.
        assertThat(activities.firedStepIds)
                .containsExactly("DPD_1_SMS", "DPD_3_SMS_EMAIL", "DPD_7_SMS_REPRESENT");
        assertThat(activities.closedStatus).isEqualTo("RESOLVED");
    }

    @Test
    void progressesThroughEveryStepToWriteOffThenClosesWrittenOff() {
        DunningWorkflow wf = start("C");
        // A single tick at DPD 180 reaches every step (escalate and write-off milestones included).
        wf.onDpdTick(new DpdTick(180, "NPA", epochDay(2027, 5, 12)));
        settle();
        wf.onWrittenOff();
        awaitDone(wf);

        assertThat(activities.firedStepIds)
                .hasSize(8)
                .contains("DPD_15_ESCALATE", "DPD_180_WRITE_OFF");
        assertThat(activities.closedStatus).isEqualTo("WRITTEN_OFF");
    }

    @Test
    void manualEscalateAndAssignAgentAreApplied() {
        DunningWorkflow wf = start("ESC");
        wf.onDpdTick(new DpdTick(3, "DPD_1_30", epochDay(2026, 11, 16)));
        settle();
        wf.onAssignAgent("agent-7");
        settle();
        wf.onEscalate("customer unreachable");
        settle();
        wf.onPaymentReceived(true);
        awaitDone(wf);

        assertThat(activities.assignments).contains("agent-7");
        assertThat(activities.escalations).anyMatch(e -> e.startsWith("ESCALATED:"));
        assertThat(activities.closedStatus).isEqualTo("RESOLVED");
    }
}
