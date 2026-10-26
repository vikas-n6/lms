package com.lms.collections.adapters.out.temporal;

import com.lms.collections.adapters.out.temporal.DunningCommands.FiredStep;
import com.lms.collections.application.port.out.CollectionCaseRepository;
import com.lms.collections.application.port.out.CollectionsEventPublisher;
import com.lms.collections.application.port.out.DunningActionRepository;
import com.lms.collections.domain.CaseStatus;
import com.lms.collections.domain.DunningAction;
import com.lms.events.Uuid7;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Side-effecting dunning activities (phase prompt task 4). Each runs in its own transaction and is
 * idempotent: {@code fireStep} inserts the unique {@code (caseId, stepId)} row and only publishes
 * when it is newly inserted, so Temporal's at-least-once delivery never produces a duplicate action
 * or event.
 */
@Component
public class DunningActivitiesImpl implements DunningActivities {

    private final CollectionCaseRepository cases;
    private final DunningActionRepository actions;
    private final CollectionsEventPublisher publisher;
    private final Clock clock;

    public DunningActivitiesImpl(
            CollectionCaseRepository cases,
            DunningActionRepository actions,
            CollectionsEventPublisher publisher,
            Clock clock) {
        this.cases = Objects.requireNonNull(cases);
        this.actions = Objects.requireNonNull(actions);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public boolean fireStep(String caseId, String loanId, String loanNumber, FiredStep step) {
        UUID caseUuid = UUID.fromString(caseId);
        DunningAction action =
                new DunningAction(
                        Uuid7.generate(clock),
                        caseUuid,
                        step.stepId(),
                        step.dpdThreshold(),
                        step.actionType(),
                        step.channel(),
                        step.templateId(),
                        clock.instant());
        boolean inserted = actions.recordIfAbsent(action);
        if (!inserted) {
            return false; // already fired for this (caseId, stepId): a replay/duplicate tick
        }
        publisher.reminderDue(caseId, loanId, loanNumber, action);
        if ("ESCALATE".equals(step.actionType())) {
            applyStatus(caseUuid, CaseStatus.ESCALATED);
        } else if ("RECOMMEND_WRITE_OFF".equals(step.actionType())) {
            applyStatus(caseUuid, CaseStatus.WRITE_OFF_RECOMMENDED);
        }
        return true;
    }

    @Override
    @Transactional
    public void markEscalated(
            String caseId, String loanId, String loanNumber, String status, String reason) {
        applyStatus(UUID.fromString(caseId), CaseStatus.valueOf(status));
        publisher.escalated(caseId, loanId, loanNumber, status, null, reason);
    }

    @Override
    @Transactional
    public void assignAgent(String caseId, String agent) {
        cases.findById(UUID.fromString(caseId))
                .ifPresent(c -> cases.update(c.withAgent(agent, clock.instant())));
    }

    @Override
    @Transactional
    public void recordPromise(
            String caseId, long promiseEpochDay, long amountMinor, String currency) {
        cases.findById(UUID.fromString(caseId))
                .ifPresent(
                        c ->
                                cases.update(
                                        c.withPromise(
                                                java.time.LocalDate.ofEpochDay(promiseEpochDay),
                                                amountMinor,
                                                currency,
                                                clock.instant())));
    }

    @Override
    @Transactional
    public void closeCase(
            String caseId, String loanId, String loanNumber, String status, String reason) {
        applyStatus(UUID.fromString(caseId), CaseStatus.valueOf(status));
        publisher.caseClosed(caseId, loanId, loanNumber, status, reason);
    }

    private void applyStatus(UUID caseId, CaseStatus status) {
        cases.findById(caseId).ifPresent(c -> cases.update(c.withStatus(status, clock.instant())));
    }
}
