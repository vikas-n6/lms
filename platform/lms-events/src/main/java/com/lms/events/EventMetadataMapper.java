package com.lms.events;

import com.lms.contracts.events.EventMetadata;
import java.util.UUID;

/** Maps between the Java {@link EventMeta} and the Avro {@link EventMetadata} wire record. */
public final class EventMetadataMapper {

    private EventMetadataMapper() {}

    public static EventMetadata toAvro(EventMeta meta) {
        return new EventMetadata(
                meta.eventId().toString(),
                meta.eventType(),
                meta.schemaVersion(),
                meta.occurredAt(),
                meta.aggregateType(),
                meta.aggregateId(),
                meta.correlationId(),
                meta.causationId() == null ? null : meta.causationId().toString(),
                meta.traceId(),
                meta.producer());
    }

    public static EventMeta fromAvro(EventMetadata avro) {
        String causationId = avro.getCausationId();
        return new EventMeta(
                UUID.fromString(avro.getEventId()),
                avro.getEventType(),
                avro.getSchemaVersion(),
                avro.getOccurredAt(),
                avro.getAggregateType(),
                avro.getAggregateId(),
                avro.getCorrelationId(),
                causationId == null ? null : UUID.fromString(causationId),
                avro.getTraceId(),
                avro.getProducer());
    }
}
