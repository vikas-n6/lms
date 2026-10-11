package com.lms.events;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * The common event envelope metadata (docs/architecture.md "Event contract"), independent of the
 * wire format. {@link #causationId} and {@link #traceId} may be null; everything else is required.
 */
public record EventMeta(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String aggregateType,
        String aggregateId,
        String correlationId,
        UUID causationId,
        String traceId,
        String producer) {

    public EventMeta {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(producer, "producer");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
        occurredAt = occurredAt.truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * Mint metadata for a brand-new event: a fresh UUIDv7 event id and {@code occurredAt} from the
     * injected clock.
     */
    public static EventMeta newEvent(
            Clock clock,
            String eventType,
            int schemaVersion,
            String aggregateType,
            String aggregateId,
            String correlationId,
            UUID causationId,
            String traceId,
            String producer) {
        return new EventMeta(
                Uuid7.generate(clock),
                eventType,
                schemaVersion,
                clock.instant(),
                aggregateType,
                aggregateId,
                correlationId,
                causationId,
                traceId,
                producer);
    }
}
