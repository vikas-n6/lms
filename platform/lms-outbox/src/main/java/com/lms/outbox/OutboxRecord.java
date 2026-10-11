package com.lms.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A row in the {@code outbox} table: a serialized event awaiting publication. Written in the same
 * transaction as the aggregate change (docs/architecture.md "Kafka rules"); the relay drains it.
 */
public record OutboxRecord(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        UUID eventId,
        String topic,
        byte[] key,
        byte[] payload,
        Map<String, byte[]> headers,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant publishedAt) {

    public boolean isPublished() {
        return publishedAt != null;
    }
}
