package com.lms.collections.adapters.out.temporal;

import com.lms.collections.adapters.out.temporal.DunningCommands.DpdTick;
import com.lms.collections.adapters.out.temporal.DunningCommands.FiredStep;
import com.lms.collections.adapters.out.temporal.DunningCommands.PromiseToPay;
import com.lms.collections.adapters.out.temporal.DunningCommands.StartDunningCommand;
import com.lms.collections.adapters.out.temporal.DunningCommands.StepDef;
import com.lms.collections.domain.CaseStatus;
import com.lms.collections.domain.DunningActionType;
import com.lms.collections.domain.DunningPlan;
import com.lms.collections.domain.DunningStep;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Deterministic dunning saga (ADR 0011). All side effects go through {@link DunningActivities}; the
 * workflow uses only {@code Workflow.await} on signal-set fields, so the history replays
 * identically (proved by the {@code WorkflowReplayer} test). It advances on DPD ticks, fires each
 * plan step once (a {@code (caseId, stepId)} row is the durable guard against duplicate ticks and
 * replays), pauses while a promise-to-pay is open, resumes when the promise is broken, and resolves
 * when a payment clears the overdue amount.
 */
public final class DunningWorkflowImpl implements DunningWorkflow {

    private DunningActivities activities;
    private StartDunningCommand command;
    private DunningPlan plan;

    private final Set<String> fired = new HashSet<>();
    private final Deque<DpdTick> pendingTicks = new ArrayDeque<>();

    private boolean paymentCleared;
    private boolean writtenOff;
    private PromiseToPay pendingPromise;
    private boolean promiseActive;
    private long promiseEpochDay;
    private String pendingEscalateReason;
    private String pendingAgent;

    private String milestone = CaseStatus.OPEN.name();
    private boolean terminal;
    private String finalStatus;

    @Override
    public void run(StartDunningCommand command) {
        this.command = command;
        this.plan = buildPlan(command);
        this.activities =
                Workflow.newActivityStub(
                        DunningActivities.class,
                        ActivityOptions.newBuilder()
                                .setStartToCloseTimeout(Duration.ofSeconds(10))
                                .build());

        while (!terminal) {
            Workflow.await(this::hasWork);
            // Apply admin signals and advance on ticks first, so the steps a day's tick reaches
            // fire before a payment that arrives in the same batch resolves the case.
            if (pendingAgent != null) {
                activities.assignAgent(command.caseId(), pendingAgent);
                pendingAgent = null;
            }
            if (pendingPromise != null) {
                promiseActive = true;
                promiseEpochDay = pendingPromise.promiseEpochDay();
                activities.recordPromise(
                        command.caseId(),
                        pendingPromise.promiseEpochDay(),
                        pendingPromise.amountMinor(),
                        pendingPromise.currency());
                pendingPromise = null;
            }
            if (pendingEscalateReason != null) {
                milestone = CaseStatus.ESCALATED.name();
                activities.markEscalated(
                        command.caseId(),
                        command.loanId(),
                        command.loanNumber(),
                        milestone,
                        pendingEscalateReason);
                pendingEscalateReason = null;
            }
            while (!pendingTicks.isEmpty()) {
                processTick(pendingTicks.poll());
            }
            // Then the terminal conditions.
            if (writtenOff) {
                close(CaseStatus.WRITTEN_OFF.name(), "loan written off");
                return;
            }
            if (paymentCleared) {
                close(CaseStatus.RESOLVED.name(), "overdue cleared");
                return;
            }
        }
    }

    private boolean hasWork() {
        return !pendingTicks.isEmpty()
                || paymentCleared
                || writtenOff
                || pendingPromise != null
                || pendingEscalateReason != null
                || pendingAgent != null;
    }

    private void processTick(DpdTick tick) {
        if (promiseActive) {
            if (tick.businessEpochDay() < promiseEpochDay) {
                return; // reminders paused until the promised date
            }
            promiseActive = false; // promise date reached while still overdue: broken, resume
        }
        for (DunningStep step : plan.stepsReachedBy(tick.daysPastDue())) {
            if (!fired.add(step.stepId())) {
                continue; // already fired for this case
            }
            activities.fireStep(
                    command.caseId(),
                    command.loanId(),
                    command.loanNumber(),
                    new FiredStep(
                            step.stepId(),
                            step.dpdThreshold(),
                            step.actionType().name(),
                            step.channel(),
                            step.templateId()));
            if (step.actionType() == DunningActionType.ESCALATE) {
                milestone = CaseStatus.ESCALATED.name();
            } else if (step.actionType() == DunningActionType.RECOMMEND_WRITE_OFF) {
                milestone = CaseStatus.WRITE_OFF_RECOMMENDED.name();
            }
        }
    }

    private void close(String status, String reason) {
        this.finalStatus = status;
        this.terminal = true;
        activities.closeCase(
                command.caseId(), command.loanId(), command.loanNumber(), status, reason);
    }

    private static DunningPlan buildPlan(StartDunningCommand command) {
        return new DunningPlan(
                command.planVersion(),
                command.steps().stream().map(DunningWorkflowImpl::toStep).toList());
    }

    private static DunningStep toStep(StepDef def) {
        return new DunningStep(
                def.stepId(),
                def.dpdThreshold(),
                DunningActionType.valueOf(def.actionType()),
                def.channel(),
                def.templateId());
    }

    @Override
    public void onDpdTick(DpdTick tick) {
        if (tick.daysPastDue() <= 0) {
            paymentCleared = true; // DPD back to zero: overdue cleared
        } else {
            pendingTicks.add(tick);
        }
    }

    @Override
    public void onPaymentReceived(boolean overdueCleared) {
        if (overdueCleared) {
            paymentCleared = true;
        }
    }

    @Override
    public void onPromiseToPay(PromiseToPay promise) {
        this.pendingPromise = promise;
    }

    @Override
    public void onEscalate(String reason) {
        this.pendingEscalateReason = reason == null ? "manual escalation" : reason;
    }

    @Override
    public void onAssignAgent(String agent) {
        this.pendingAgent = agent;
    }

    @Override
    public void onWrittenOff() {
        this.writtenOff = true;
    }

    @Override
    public String currentStatus() {
        if (terminal) {
            return finalStatus;
        }
        return promiseActive ? CaseStatus.PROMISE_TO_PAY.name() : milestone;
    }
}
