package com.lms.collections.adapters.out.temporal;

import com.lms.collections.adapters.out.temporal.DunningCommands.DpdTick;
import com.lms.collections.adapters.out.temporal.DunningCommands.StartDunningCommand;
import com.lms.collections.adapters.out.temporal.DunningCommands.StepDef;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves the dunning workflow is deterministic (phase prompt: crash test / "restart without
 * repeating actions"). It runs an execution through several DPD ticks and a resolving payment,
 * captures the history, and replays it against the workflow code; any non-determinism surfaces as a
 * replay failure here.
 */
class DunningReplayDeterminismTest {

    private static final String TASK_QUEUE = "collections-replay";
    private static final String WORKFLOW_ID = "dun-loan-replay";

    @Test
    void recordedHistoryReplaysWithoutNonDeterminism() throws Exception {
        TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance();
        try {
            Worker worker = env.newWorker(TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(DunningWorkflowImpl.class);
            worker.registerActivitiesImplementations(new FakeDunningActivities());
            env.start();

            DunningWorkflow wf =
                    env.getWorkflowClient()
                            .newWorkflowStub(
                                    DunningWorkflow.class,
                                    WorkflowOptions.newBuilder()
                                            .setTaskQueue(TASK_QUEUE)
                                            .setWorkflowId(WORKFLOW_ID)
                                            .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                                            .build());
            WorkflowClient.start(
                    wf::run,
                    new StartDunningCommand(
                            "case-replay",
                            "loan-replay",
                            "LN-REPLAY",
                            1,
                            List.of(
                                    new StepDef("DPD_1_SMS", 1, "SMS", "SMS", "d.dpd1"),
                                    new StepDef(
                                            "DPD_7_SMS", 7, "SMS_REPRESENT", "SMS", "d.dpd7"))));
            wf.onDpdTick(new DpdTick(1, "DPD_1_30", LocalDate.of(2026, 11, 14).toEpochDay()));
            wf.onDpdTick(new DpdTick(7, "DPD_1_30", LocalDate.of(2026, 11, 20).toEpochDay()));
            wf.onPaymentReceived(true);
            WorkflowStub.fromTyped(wf).getResult(Void.class);

            WorkflowExecutionHistory history = env.getWorkflowClient().fetchHistory(WORKFLOW_ID);
            WorkflowReplayer.replayWorkflowExecution(history, DunningWorkflowImpl.class);
        } finally {
            env.close();
        }
    }
}
