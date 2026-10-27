package com.lms.reporting.adapters.in.kafka;

import com.lms.contracts.collections.CollectionsCaseClosed;
import com.lms.contracts.collections.CollectionsCaseOpened;
import com.lms.contracts.collections.CollectionsEscalated;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.reporting.application.CollectionCaseProjection;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Projects collections.events onto the collection-case read model (ADR 0013). */
@Component
public class CollectionsEventsListener {

    static final String TOPIC = "collections.events";

    private final CollectionCaseProjection projection;
    private final IdempotentConsumer idempotent;

    public CollectionsEventsListener(
            CollectionCaseProjection projection, IdempotentConsumer idempotent) {
        this.projection = projection;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "reporting")
    @Transactional
    public void onMessage(Object payload) {
        switch (payload) {
            case CollectionsCaseOpened e ->
                    run(e.getMetadata().getEventId(), () -> projection.onOpened(e));
            case CollectionsEscalated e ->
                    run(e.getMetadata().getEventId(), () -> projection.onEscalated(e));
            case CollectionsCaseClosed e ->
                    run(e.getMetadata().getEventId(), () -> projection.onClosed(e));
            default -> {
                // CollectionsReminderDue has no case-level read-model impact; ignore.
            }
        }
    }

    private void run(String eventId, Runnable handler) {
        idempotent.process(UUID.fromString(eventId), TOPIC, handler);
    }
}
