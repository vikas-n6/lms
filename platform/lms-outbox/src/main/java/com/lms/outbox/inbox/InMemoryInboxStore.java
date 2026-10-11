package com.lms.outbox.inbox;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link InboxStore} for tests and single-node local runs. */
public final class InMemoryInboxStore implements InboxStore {

    private final ConcurrentHashMap<UUID, String> processed = new ConcurrentHashMap<>();

    @Override
    public boolean markProcessed(UUID eventId, String topic) {
        return processed.putIfAbsent(eventId, topic) == null;
    }
}
