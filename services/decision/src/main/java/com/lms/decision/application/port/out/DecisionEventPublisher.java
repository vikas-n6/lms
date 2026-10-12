package com.lms.decision.application.port.out;

import com.lms.decision.domain.Decision;

/**
 * Publishes {@code decision.completed} via the transactional outbox, in the same transaction as the
 * decision save (docs/architecture.md), so the event is never emitted without the decision being
 * stored, and vice versa.
 */
public interface DecisionEventPublisher {
    void decisionCompleted(Decision decision);
}
