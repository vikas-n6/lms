package com.lms.outbox.inbox;

import java.util.Objects;
import java.util.UUID;

/**
 * Runs an event handler at most once per event id. The inbox mark and the handler must execute in
 * one transaction (the caller provides the transactional boundary); a duplicate delivery is
 * acknowledged and skipped (docs/architecture.md "Kafka rules").
 */
public final class IdempotentConsumer {

    private final InboxStore inbox;

    public IdempotentConsumer(InboxStore inbox) {
        this.inbox = Objects.requireNonNull(inbox, "inbox");
    }

    /**
     * Process the event unless it was already handled. Returns {@code true} if the handler ran,
     * {@code false} if the event was a duplicate and was skipped.
     */
    public boolean process(UUID eventId, String topic, Runnable handler) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(handler, "handler");
        if (!inbox.markProcessed(eventId, topic)) {
            return false;
        }
        handler.run();
        return true;
    }
}
