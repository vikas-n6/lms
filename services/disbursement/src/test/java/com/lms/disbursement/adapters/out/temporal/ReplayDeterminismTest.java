package com.lms.disbursement.adapters.out.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Proves the payout workflow is deterministic (phase prompt: "Workflow replay test with
 * WorkflowReplayer on recorded histories"). It runs a happy-path execution in the in-JVM test
 * environment, captures the resulting history, and replays it against the workflow code: a
 * non-deterministic change would surface as a replay failure here.
 */
class ReplayDeterminismTest {

    private static final String TASK_QUEUE = "disbursement-replay";
    private static final String WORKFLOW_ID = "disb-loan-replay";

    @Test
    void recordedHistoryReplaysWithoutNonDeterminism() throws Exception {
        TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance();
        try {
            Worker worker = env.newWorker(TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(DisbursementWorkflowImpl.class);
            worker.registerActivitiesImplementations(new FakeDisbursementActivities());
            env.start();

            DisbursementWorkflow wf =
                    env.getWorkflowClient()
                            .newWorkflowStub(
                                    DisbursementWorkflow.class,
                                    WorkflowOptions.newBuilder()
                                            .setTaskQueue(TASK_QUEUE)
                                            .setWorkflowId(WORKFLOW_ID)
                                            .setWorkflowExecutionTimeout(Duration.ofMinutes(2))
                                            .build());
            WorkflowClient.start(
                    wf::run,
                    new StartDisbursementCommand(
                            "disb-replay",
                            "loan-replay",
                            "LN-REPLAY",
                            new SagaTimeouts(10, 3, 2, 3, 2, 2.0, 10)));
            wf.onRailResult(RailResult.success("RAIL-9", "UTR-9"));
            WorkflowStub.fromTyped(wf).getResult(Void.class);

            WorkflowExecutionHistory history = env.getWorkflowClient().fetchHistory(WORKFLOW_ID);
            // Throws on any non-determinism; a clean return is the assertion.
            WorkflowReplayer.replayWorkflowExecution(history, DisbursementWorkflowImpl.class);
        } finally {
            env.close();
        }
    }
}
