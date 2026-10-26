package com.lms.collections.adapters.out.events;

import com.lms.collections.application.port.out.CollectionsEventPublisher;
import com.lms.collections.domain.CollectionCase;
import com.lms.collections.domain.DunningAction;
import com.lms.contracts.collections.CollectionsCaseClosed;
import com.lms.contracts.collections.CollectionsCaseOpened;
import com.lms.contracts.collections.CollectionsEscalated;
import com.lms.contracts.collections.CollectionsReminderDue;
import com.lms.events.EventHeaders;
import com.lms.events.EventMeta;
import com.lms.events.EventMetadataMapper;
import com.lms.events.Uuid7;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import com.lms.security.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.stereotype.Component;

/**
 * Writes collection events to the outbox in the same transaction as the case change (never straight
 * to Kafka — docs/architecture.md). Keyed by loan id so a loan's collection events stay ordered.
 */
@Component
public class OutboxCollectionsEventPublisher implements CollectionsEventPublisher {

    private static final String TOPIC = "collections.events";
    private static final String AGGREGATE = "loan";
    private static final String PRODUCER = "collections";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outbox;
    private final Serializer<Object> serializer;
    private final Clock clock;

    public OutboxCollectionsEventPublisher(
            OutboxRepository outbox, Serializer<Object> serializer, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void caseOpened(CollectionCase c) {
        EventMeta meta = meta("collections.case-opened", c.loanId());
        CollectionsCaseOpened event =
                CollectionsCaseOpened.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setCaseId(c.id().toString())
                        .setLoanId(c.loanId())
                        .setLoanNumber(c.loanNumber())
                        .setBucket(c.bucket())
                        .setDaysPastDue(c.daysPastDue())
                        .setPlanVersion(c.planVersion())
                        .setOpenedAt(clock.instant())
                        .build();
        append(meta, c.loanId(), event);
    }

    @Override
    public void reminderDue(String caseId, String loanId, String loanNumber, DunningAction a) {
        EventMeta meta = meta("collections.reminder-due", loanId);
        CollectionsReminderDue event =
                CollectionsReminderDue.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setCaseId(caseId)
                        .setLoanId(loanId)
                        .setLoanNumber(loanNumber)
                        .setStepId(a.stepId())
                        .setDpdThreshold(a.dpdThreshold())
                        .setActionType(a.actionType())
                        .setChannel(a.channel() == null ? "NONE" : a.channel())
                        .setTemplateId(a.templateId() == null ? "" : a.templateId())
                        .setFiredAt(a.firedAt())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void escalated(
            String caseId,
            String loanId,
            String loanNumber,
            String status,
            String stepId,
            String reason) {
        EventMeta meta = meta("collections.escalated", loanId);
        CollectionsEscalated event =
                CollectionsEscalated.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setCaseId(caseId)
                        .setLoanId(loanId)
                        .setLoanNumber(loanNumber)
                        .setStatus(status)
                        .setStepId(stepId)
                        .setReason(reason)
                        .setEscalatedAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void caseClosed(
            String caseId, String loanId, String loanNumber, String status, String reason) {
        EventMeta meta = meta("collections.case-closed", loanId);
        CollectionsCaseClosed event =
                CollectionsCaseClosed.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setCaseId(caseId)
                        .setLoanId(loanId)
                        .setLoanNumber(loanNumber)
                        .setStatus(status)
                        .setReason(reason)
                        .setClosedAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    private EventMeta meta(String eventType, String loanId) {
        return EventMeta.newEvent(
                clock,
                eventType,
                SCHEMA_VERSION,
                AGGREGATE,
                loanId,
                loanId,
                null,
                RequestContextHolder.currentIfBound().map(ctx -> ctx.traceId()).orElse(null),
                PRODUCER);
    }

    private void append(EventMeta meta, String loanId, SpecificRecord event) {
        byte[] payload = serializer.serialize(TOPIC, event);
        OutboxRecord row =
                new OutboxRecord(
                        Uuid7.generate(clock),
                        AGGREGATE,
                        loanId,
                        meta.eventType(),
                        meta.eventId(),
                        TOPIC,
                        loanId.getBytes(StandardCharsets.UTF_8),
                        payload,
                        EventHeaders.toHeaders(meta),
                        0,
                        null,
                        clock.instant(),
                        null);
        outbox.append(row);
    }
}
