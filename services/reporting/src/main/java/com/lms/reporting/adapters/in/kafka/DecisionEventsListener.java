package com.lms.reporting.adapters.in.kafka;

import com.lms.contracts.decision.DecisionCompleted;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.reporting.application.ApplicationProjection;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Projects decision.events onto the funnel's approved/rejected stages (ADR 0013). */
@Component
public class DecisionEventsListener {

    static final String TOPIC = "decision.events";

    private final ApplicationProjection projection;
    private final IdempotentConsumer idempotent;

    public DecisionEventsListener(ApplicationProjection projection, IdempotentConsumer idempotent) {
        this.projection = projection;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "reporting")
    @Transactional
    public void onMessage(Object payload) {
        if (payload instanceof DecisionCompleted e) {
            idempotent.process(
                    UUID.fromString(e.getMetadata().getEventId()),
                    TOPIC,
                    () -> projection.onDecisionCompleted(e));
        }
    }
}
