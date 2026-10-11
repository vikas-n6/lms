package com.lms.events;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka header propagation for trace and correlation ids. Adapters convert this map to and from
 * {@code org.apache.kafka.common.header.Headers} so the platform stays Kafka-API-light here.
 */
public final class EventHeaders {

    public static final String EVENT_ID = "lms-event-id";
    public static final String EVENT_TYPE = "lms-event-type";
    public static final String CORRELATION_ID = "lms-correlation-id";
    public static final String CAUSATION_ID = "lms-causation-id";
    public static final String TRACE_ID = "lms-trace-id";

    private EventHeaders() {}

    /** Project an envelope's propagation ids into a header map (null-valued ids are omitted). */
    public static Map<String, byte[]> toHeaders(EventMeta meta) {
        Map<String, byte[]> headers = new HashMap<>();
        put(headers, EVENT_ID, meta.eventId().toString());
        put(headers, EVENT_TYPE, meta.eventType());
        put(headers, CORRELATION_ID, meta.correlationId());
        put(
                headers,
                CAUSATION_ID,
                meta.causationId() == null ? null : meta.causationId().toString());
        put(headers, TRACE_ID, meta.traceId());
        return headers;
    }

    public static String asString(byte[] value) {
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    private static void put(Map<String, byte[]> headers, String key, String value) {
        if (value != null) {
            headers.put(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
