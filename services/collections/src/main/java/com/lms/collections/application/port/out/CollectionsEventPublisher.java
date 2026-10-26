package com.lms.collections.application.port.out;

import com.lms.collections.domain.CollectionCase;
import com.lms.collections.domain.DunningAction;

/**
 * Publishes collection events onto {@code collections.events} through the outbox (phase prompt task
 * 4, never straight to Kafka — docs/architecture.md). Keyed by loan id.
 */
public interface CollectionsEventPublisher {

    void caseOpened(CollectionCase c);

    void reminderDue(String caseId, String loanId, String loanNumber, DunningAction action);

    void escalated(
            String caseId,
            String loanId,
            String loanNumber,
            String status,
            String stepId,
            String reason);

    void caseClosed(String caseId, String loanId, String loanNumber, String status, String reason);
}
