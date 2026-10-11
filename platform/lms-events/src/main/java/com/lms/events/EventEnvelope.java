package com.lms.events;

import java.util.Objects;

/**
 * An event: its {@link EventMeta} envelope plus a typed payload. The application layer works with
 * this; adapters map it to/from the Avro wire records in {@code contracts}.
 */
public record EventEnvelope<P>(EventMeta meta, P payload) {

    public EventEnvelope {
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(payload, "payload");
    }

    /** The Kafka message key: the aggregate id, so ordering is per aggregate. */
    public String key() {
        return meta.aggregateId();
    }
}
