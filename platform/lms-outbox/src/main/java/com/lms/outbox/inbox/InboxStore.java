package com.lms.outbox.inbox;

import java.util.UUID;

/** Records which event ids have been processed, for consumer idempotency. */
public interface InboxStore {

    /**
     * Mark an event id as processed. Returns {@code true} if it was newly recorded, {@code false}
     * if it was already present (a duplicate delivery). Must run in the same transaction as the
     * handler so a rollback also undoes the mark.
     */
    boolean markProcessed(UUID eventId, String topic);
}
