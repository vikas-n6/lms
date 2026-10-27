package com.lms.loanaccount.adapters.out.events;

import com.lms.contracts.audit.AuditEvent;
import com.lms.events.EventHeaders;
import com.lms.events.EventMeta;
import com.lms.events.EventMetadataMapper;
import com.lms.events.Uuid7;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import com.lms.security.audit.AuditEntry;
import com.lms.security.audit.AuditRecorder;
import com.lms.security.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Writes an {@link AuditEntry} to the {@code audit.events} outbox in the same transaction as the
 * audited admin action (ADR 0014) — so the change and its audit row commit together, and publishing
 * still goes only through the outbox (never straight to Kafka, CLAUDE.md). Keyed by actor id so one
 * actor's actions stay ordered; the correlation and trace ids come from the request context.
 */
@Component
public class OutboxAuditRecorder implements AuditRecorder {

    private static final String TOPIC = "audit.events";
    private static final String AGGREGATE = "actor";
    private static final String PRODUCER = "loan-account";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outbox;
    private final org.apache.kafka.common.serialization.Serializer<Object> serializer;
    private final Clock clock;

    public OutboxAuditRecorder(
            OutboxRepository outbox,
            org.apache.kafka.common.serialization.Serializer<Object> serializer,
            Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void record(AuditEntry entry) {
        String actorId = entry.actorId();
        EventMeta meta =
                EventMeta.newEvent(
                        clock,
                        "audit.recorded",
                        SCHEMA_VERSION,
                        AGGREGATE,
                        actorId,
                        RequestContextHolder.current().traceId() == null
                                ? actorId
                                : RequestContextHolder.current().traceId(),
                        null,
                        RequestContextHolder.current().traceId(),
                        PRODUCER);
        AuditEvent event =
                AuditEvent.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setActorId(actorId)
                        .setActorRoles(entry.actorRoles())
                        .setAction(entry.action())
                        .setTargetType(entry.targetType())
                        .setTargetId(entry.targetId())
                        .setBeforeSummary(entry.beforeSummary())
                        .setAfterSummary(entry.afterSummary())
                        .setOutcome(entry.outcome().name())
                        .setRecordedAt(clock.instant())
                        .build();
        byte[] payload = serializer.serialize(TOPIC, event);
        OutboxRecord row =
                new OutboxRecord(
                        Uuid7.generate(clock),
                        AGGREGATE,
                        actorId,
                        meta.eventType(),
                        meta.eventId(),
                        TOPIC,
                        actorId.getBytes(StandardCharsets.UTF_8),
                        payload,
                        EventHeaders.toHeaders(meta),
                        0,
                        null,
                        clock.instant(),
                        null);
        outbox.append(row);
    }
}
