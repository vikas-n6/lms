package com.lms.disbursement.adapters.out.events;

import com.lms.contracts.disbursement.DisbursementFailed;
import com.lms.contracts.disbursement.DisbursementInitiated;
import com.lms.contracts.disbursement.DisbursementSucceeded;
import com.lms.contracts.events.MoneyAmount;
import com.lms.disbursement.application.port.out.DisbursementEventPublisher;
import com.lms.disbursement.domain.Disbursement;
import com.lms.events.EventHeaders;
import com.lms.events.EventMeta;
import com.lms.events.EventMetadataMapper;
import com.lms.events.Uuid7;
import com.lms.money.Money;
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
 * Writes disbursement lifecycle events to the outbox in the same transaction as the disbursement
 * change (never straight to Kafka — docs/architecture.md). Keyed by loanId so events for one loan
 * stay ordered.
 */
@Component
public class OutboxDisbursementEventPublisher implements DisbursementEventPublisher {

    private static final String TOPIC = "disbursement.events";
    private static final String AGGREGATE = "loan";
    private static final String PRODUCER = "disbursement";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outbox;
    private final Serializer<Object> serializer;
    private final Clock clock;

    public OutboxDisbursementEventPublisher(
            OutboxRepository outbox, Serializer<Object> serializer, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void initiated(Disbursement d) {
        String loanId = d.loanId().toString();
        EventMeta meta = meta("disbursement.initiated", loanId);
        DisbursementInitiated event =
                DisbursementInitiated.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setDisbursementId(d.id().toString())
                        .setLoanId(loanId)
                        .setNetAmount(money(d.net()))
                        .setInitiatedAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void succeeded(Disbursement d) {
        String loanId = d.loanId().toString();
        EventMeta meta = meta("disbursement.succeeded", loanId);
        DisbursementSucceeded event =
                DisbursementSucceeded.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setDisbursementId(d.id().toString())
                        .setLoanId(loanId)
                        .setPrincipal(money(d.principal()))
                        .setFee(money(d.fee()))
                        .setGst(money(d.gst()))
                        .setNetAmount(money(d.net()))
                        .setDisbursementDate(d.disbursementDate())
                        .setRailReference(d.railRef())
                        .setSucceededAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void failed(Disbursement d) {
        String loanId = d.loanId().toString();
        EventMeta meta = meta("disbursement.failed", loanId);
        DisbursementFailed event =
                DisbursementFailed.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setDisbursementId(d.id().toString())
                        .setLoanId(loanId)
                        .setNetAmount(money(d.net()))
                        .setReason(d.failureReason() == null ? "RAIL_FAILED" : d.failureReason())
                        .setFailedAt(clock.instant())
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
                RequestContextHolder.current().traceId(),
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

    private static MoneyAmount money(Money money) {
        return MoneyAmount.newBuilder()
                .setMinorUnits(money.minorUnits())
                .setCurrency(money.currency().getCurrencyCode())
                .build();
    }
}
