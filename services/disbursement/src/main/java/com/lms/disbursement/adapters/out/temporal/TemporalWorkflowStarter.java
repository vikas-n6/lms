package com.lms.disbursement.adapters.out.temporal;

import com.lms.disbursement.application.port.out.WorkflowStarter;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Temporal adapter for {@link WorkflowStarter}. The workflow id {@code disb-{loanId}} makes a start
 * idempotent: a duplicate start throws {@link WorkflowExecutionAlreadyStarted}, which is swallowed
 * so a redelivered {@code loan.created} never launches a second saga. Signals are addressed by the
 * same id. Exercised in the Docker session.
 */
@Component
public class TemporalWorkflowStarter implements WorkflowStarter {

    private static final Logger log = LoggerFactory.getLogger(TemporalWorkflowStarter.class);

    private final WorkflowClient client;
    private final String taskQueue;
    private final SagaTimeouts timeouts;

    public TemporalWorkflowStarter(
            WorkflowClient client,
            @Value("${lms.temporal.task-queue:disbursement}") String disbursementTaskQueue,
            SagaTimeouts sagaTimeouts) {
        this.client = client;
        this.taskQueue = disbursementTaskQueue;
        this.timeouts = sagaTimeouts;
    }

    @Override
    public void startDisbursement(UUID disbursementId, UUID loanId, String loanNumber) {
        DisbursementWorkflow workflow =
                client.newWorkflowStub(
                        DisbursementWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setTaskQueue(taskQueue)
                                .setWorkflowId(workflowId(loanId))
                                .build());
        try {
            WorkflowClient.start(
                    workflow::run,
                    new StartDisbursementCommand(
                            disbursementId.toString(), loanId.toString(), loanNumber, timeouts));
        } catch (WorkflowExecutionAlreadyStarted alreadyStarted) {
            log.info("disbursement workflow for loan {} already started; no-op", loanId);
        }
    }

    @Override
    public void signalRailResult(UUID loanId, RailSignal signal) {
        RailResult result =
                signal.success()
                        ? RailResult.success(signal.railRef(), signal.utr())
                        : RailResult.failed(signal.railRef(), signal.failureCode());
        stub(loanId).onRailResult(result);
    }

    @Override
    public void signalAdminDecision(UUID loanId, boolean retry) {
        stub(loanId).onAdminDecision(retry ? AdminDecision.RETRY : AdminDecision.CANCEL);
    }

    private DisbursementWorkflow stub(UUID loanId) {
        return client.newWorkflowStub(DisbursementWorkflow.class, workflowId(loanId));
    }

    private static String workflowId(UUID loanId) {
        return "disb-" + loanId;
    }
}
