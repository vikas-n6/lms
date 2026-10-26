package com.lms.collections.adapters.out.temporal;

import com.lms.collections.adapters.out.temporal.DunningCommands.DpdTick;
import com.lms.collections.adapters.out.temporal.DunningCommands.PromiseToPay;
import com.lms.collections.adapters.out.temporal.DunningCommands.StartDunningCommand;
import com.lms.collections.adapters.out.temporal.DunningCommands.StepDef;
import com.lms.collections.application.port.out.DunningWorkflowClient;
import com.lms.collections.domain.CollectionCase;
import com.lms.collections.domain.DunningPlan;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Temporal adapter for {@link DunningWorkflowClient}. The workflow id {@code dun-{loanId}} makes a
 * start idempotent — a duplicate start throws {@link WorkflowExecutionAlreadyStarted}, swallowed so
 * a redelivered open never launches a second workflow — and addresses every signal. Exercised
 * against the live Temporal dev server in the Docker session.
 */
@Component
public class DunningWorkflowStarter implements DunningWorkflowClient {

    private static final Logger log = LoggerFactory.getLogger(DunningWorkflowStarter.class);

    private final WorkflowClient client;
    private final String taskQueue;

    public DunningWorkflowStarter(
            WorkflowClient client,
            @Value("${lms.temporal.task-queue:collections}") String taskQueue) {
        this.client = client;
        this.taskQueue = taskQueue;
    }

    @Override
    public void start(CollectionCase c, DunningPlan plan) {
        DunningWorkflow workflow =
                client.newWorkflowStub(
                        DunningWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setTaskQueue(taskQueue)
                                .setWorkflowId(workflowId(c.loanId()))
                                .build());
        List<StepDef> steps =
                plan.steps().stream()
                        .map(
                                s ->
                                        new StepDef(
                                                s.stepId(),
                                                s.dpdThreshold(),
                                                s.actionType().name(),
                                                s.channel(),
                                                s.templateId()))
                        .toList();
        try {
            WorkflowClient.start(
                    workflow::run,
                    new StartDunningCommand(
                            c.id().toString(), c.loanId(), c.loanNumber(), plan.version(), steps));
        } catch (WorkflowExecutionAlreadyStarted alreadyStarted) {
            log.info("dunning workflow for loan {} already started; no-op", c.loanId());
        }
    }

    @Override
    public void signalDpdTick(
            String loanId, int daysPastDue, String bucket, LocalDate businessDate) {
        stub(loanId).onDpdTick(new DpdTick(daysPastDue, bucket, businessDate.toEpochDay()));
    }

    @Override
    public void signalPaymentReceived(String loanId, boolean overdueCleared) {
        stub(loanId).onPaymentReceived(overdueCleared);
    }

    @Override
    public void signalPromiseToPay(
            String loanId, LocalDate promiseDate, long amountMinor, String currency) {
        stub(loanId)
                .onPromiseToPay(new PromiseToPay(promiseDate.toEpochDay(), amountMinor, currency));
    }

    @Override
    public void signalEscalate(String loanId, String reason) {
        stub(loanId).onEscalate(reason);
    }

    @Override
    public void signalAssignAgent(String loanId, String agent) {
        stub(loanId).onAssignAgent(agent);
    }

    @Override
    public void signalWrittenOff(String loanId) {
        stub(loanId).onWrittenOff();
    }

    private DunningWorkflow stub(String loanId) {
        return client.newWorkflowStub(DunningWorkflow.class, workflowId(loanId));
    }

    private static String workflowId(String loanId) {
        return "dun-" + loanId;
    }
}
