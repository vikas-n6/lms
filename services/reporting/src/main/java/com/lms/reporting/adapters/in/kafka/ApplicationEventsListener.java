package com.lms.reporting.adapters.in.kafka;

import com.lms.contracts.application.ApplicationAccepted;
import com.lms.contracts.application.ApplicationExpired;
import com.lms.contracts.application.ApplicationOffered;
import com.lms.contracts.application.ApplicationRejected;
import com.lms.contracts.application.ApplicationSubmitted;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.reporting.application.ApplicationProjection;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Projects application.events onto the funnel read model (ADR 0013). */
@Component
public class ApplicationEventsListener {

    static final String TOPIC = "application.events";

    private final ApplicationProjection projection;
    private final IdempotentConsumer idempotent;

    public ApplicationEventsListener(
            ApplicationProjection projection, IdempotentConsumer idempotent) {
        this.projection = projection;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "reporting")
    @Transactional
    public void onMessage(Object payload) {
        switch (payload) {
            case ApplicationSubmitted e ->
                    run(e.getMetadata().getEventId(), () -> projection.onSubmitted(e));
            case ApplicationOffered e ->
                    run(e.getMetadata().getEventId(), () -> projection.onOffered(e));
            case ApplicationAccepted e ->
                    run(e.getMetadata().getEventId(), () -> projection.onAccepted(e));
            case ApplicationRejected e ->
                    run(e.getMetadata().getEventId(), () -> projection.onRejected(e));
            case ApplicationExpired e ->
                    run(e.getMetadata().getEventId(), () -> projection.onExpired(e));
            default -> {
                // Other application.events carry no funnel impact; ignore.
            }
        }
    }

    private void run(String eventId, Runnable handler) {
        idempotent.process(UUID.fromString(eventId), TOPIC, handler);
    }
}
