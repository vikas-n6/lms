package com.lms.repayment.adapters.out.events;

import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.repayment.MandateDebitFailed;
import com.lms.contracts.repayment.MandateRegistered;
import com.lms.contracts.repayment.RepaymentReceived;
import com.lms.contracts.repayment.RepaymentReversed;
import com.lms.events.EventHeaders;
import com.lms.events.EventMeta;
import com.lms.events.EventMetadataMapper;
import com.lms.events.Uuid7;
import com.lms.money.Money;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import com.lms.repayment.application.port.out.RepaymentEventPublisher;
import com.lms.repayment.domain.Mandate;
import com.lms.repayment.domain.Repayment;
import com.lms.security.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.stereotype.Component;

/**
 * Writes repayment/mandate events to the outbox in the same transaction as the state change (never
 * straight to Kafka — docs/architecture.md). Keyed by loanId so events for one loan stay ordered.
 */
@Component
public class OutboxRepaymentEventPublisher implements RepaymentEventPublisher {

    private static final String TOPIC = "repayment.events";
    private static final String AGGREGATE = "loan";
    private static final String PRODUCER = "repayment";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outbox;
    private final Serializer<Object> serializer;
    private final Clock clock;

    public OutboxRepaymentEventPublisher(
            OutboxRepository outbox, Serializer<Object> serializer, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox);
        this.serializer = Objects.requireNonNull(serializer);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void repaymentReceived(Repayment r) {
        String loanId = r.loanId().toString();
        EventMeta meta = meta("repayment.received", loanId);
        RepaymentReceived event =
                RepaymentReceived.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setRepaymentId(r.id().toString())
                        .setLoanId(loanId)
                        .setAmount(money(r.amount()))
                        .setRailReference(r.providerReference())
                        .setChannel(r.channel().name())
                        .setPurpose(r.purpose())
                        .setForeclosureQuoteId(r.foreclosureQuoteId())
                        .setReceivedAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void repaymentReversed(Repayment r, String reason) {
        String loanId = r.loanId().toString();
        EventMeta meta = meta("repayment.reversed", loanId);
        RepaymentReversed event =
                RepaymentReversed.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setRepaymentId(r.id().toString())
                        .setLoanId(loanId)
                        .setAmount(money(r.amount()))
                        .setRailReference(r.providerReference())
                        .setReason(reason)
                        .setReversedAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void mandateRegistered(Mandate m) {
        String loanId = m.loanId().toString();
        EventMeta meta = meta("mandate.registered", loanId);
        MandateRegistered event =
                MandateRegistered.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setMandateId(m.id().toString())
                        .setLoanId(loanId)
                        .setCustomerId(m.customerId())
                        .setStatus(m.status().name())
                        .setRegisteredAt(clock.instant())
                        .build();
        append(meta, loanId, event);
    }

    @Override
    public void mandateDebitFailed(
            UUID loanId,
            String mandateId,
            Money amount,
            LocalDate dueDate,
            String failureCode,
            LocalDate rePresentOn) {
        String id = loanId.toString();
        EventMeta meta = meta("mandate.debit-failed", id);
        MandateDebitFailed event =
                MandateDebitFailed.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setMandateId(mandateId)
                        .setLoanId(id)
                        .setAmount(money(amount))
                        .setDueDate(dueDate)
                        .setFailureCode(failureCode)
                        .setRePresentOn(rePresentOn)
                        .setFailedAt(clock.instant())
                        .build();
        append(meta, id, event);
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
