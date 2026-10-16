package com.lms.disbursement.adapters.out.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the payout saga in the in-JVM, time-skipping {@link TestWorkflowEnvironment} (ADR 0008):
 * happy path via callback signal, resolution by status polling after a timeout, the unknown-outcome
 * rule (one payout, status-query resolves it), bounded retries ending in FAILED, and the
 * manual-review → admin-cancel path. No Docker, no real rail, DB or Kafka.
 */
class DisbursementWorkflowTest {

    private static final String TASK_QUEUE = "disbursement";

    private TestWorkflowEnvironment env;
    private FakeDisbursementActivities activities;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        activities = new FakeDisbursementActivities();
        Worker worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(DisbursementWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private static SagaTimeouts timeouts(int maxAttempts) {
        // Short values; the test environment skips all waits instantly.
        return new SagaTimeouts(10, 3, 2, maxAttempts, 2, 2.0, 10);
    }

    private DisbursementWorkflow start(int maxAttempts) {
        DisbursementWorkflow wf =
                env.getWorkflowClient()
                        .newWorkflowStub(
                                DisbursementWorkflow.class,
                                WorkflowOptions.newBuilder()
                                        .setTaskQueue(TASK_QUEUE)
                                        .setWorkflowId("disb-loan-1")
                                        // Bound the run so a dropped signal fails fast (the test
                                        // clock skips to this) instead of hanging the build.
                                        .setWorkflowExecutionTimeout(
                                                java.time.Duration.ofMinutes(2))
                                        .build());
        WorkflowClient.start(
                wf::run,
                new StartDisbursementCommand("disb-1", "loan-1", "LN-1", timeouts(maxAttempts)));
        return wf;
    }

    private static void awaitDone(DisbursementWorkflow wf) {
        WorkflowStub.fromTyped(wf).getResult(Void.class);
    }

    @Test
    void happyPathSucceedsOnCallbackSignal() {
        DisbursementWorkflow wf = start(3);
        wf.onRailResult(RailResult.success("RAIL-9", "UTR-9"));
        awaitDone(wf);

        assertThat(activities.initiatedCount.get()).isEqualTo(1);
        assertThat(activities.succeededCount).isEqualTo(1);
        assertThat(activities.succeededUtr).isEqualTo("UTR-9");
        assertThat(activities.callRailCount.get()).isEqualTo(1);
        assertThat(wf.currentStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void timeoutWithoutCallbackIsResolvedByStatusPolling() {
        activities.queryStatusResult = RailResult.success("RAIL-7", "UTR-7");
        DisbursementWorkflow wf = start(3);
        awaitDone(wf); // no signal; timer is skipped, then status poll resolves

        assertThat(activities.succeededCount).isEqualTo(1);
        assertThat(activities.queryStatusCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(wf.currentStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void lostResponseQueriesStatusAndNeverRepays() {
        activities.defaultCallRail = RailResult.unknown(); // POST response lost
        activities.queryStatusResult = RailResult.success("RAIL-5", "UTR-5");
        DisbursementWorkflow wf = start(3);
        awaitDone(wf);

        assertThat(activities.callRailCount.get()).isEqualTo(1); // exactly one payout
        assertThat(activities.queryStatusCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(activities.succeededCount).isEqualTo(1);
    }

    @Test
    void threeDefiniteFailuresEndInFailed() {
        activities.queryStatusResult = RailResult.failed("RAIL-x", "RAIL_DECLINED");
        DisbursementWorkflow wf = start(3);
        awaitDone(wf); // no callback; each attempt times out then polls FAILED

        assertThat(activities.startAttemptCount.get()).isEqualTo(3);
        assertThat(activities.failedCount).isEqualTo(1);
        assertThat(activities.failReason).isEqualTo("RAIL_DECLINED");
        assertThat(wf.currentStatus()).isEqualTo("FAILED");
    }

    @Test
    void unresolvableResultParksInManualReviewThenAdminCancels() {
        activities.queryStatusResult = RailResult.unknown(); // never resolves
        DisbursementWorkflow wf = start(3);
        wf.onAdminDecision(AdminDecision.CANCEL);
        awaitDone(wf);

        assertThat(activities.manualReviewCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(activities.failedCount).isEqualTo(1);
        assertThat(activities.failReason).isEqualTo("CANCELLED_IN_REVIEW");
        assertThat(wf.currentStatus()).isEqualTo("FAILED");
    }

    @Test
    void complianceRejectionFailsBeforeAnyPayout() {
        activities.compliance = false;
        DisbursementWorkflow wf = start(3);
        awaitDone(wf);

        assertThat(activities.callRailCount.get()).isZero();
        assertThat(activities.failedCount).isEqualTo(1);
        assertThat(activities.failReason).isEqualTo("COMPLIANCE_REJECTED");
    }
}
