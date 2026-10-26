package com.lms.ledger.adapters.out.events;

import com.lms.contracts.ledger.LedgerEntryPosted;
import com.lms.contracts.ledger.LedgerEntryReversed;
import com.lms.contracts.ledger.LedgerReconCompleted;
import com.lms.events.EventHeaders;
import com.lms.events.EventMeta;
import com.lms.events.EventMetadataMapper;
import com.lms.events.Uuid7;
import com.lms.ledger.application.port.out.LedgerEventPublisher;
import com.lms.ledger.domain.JournalEntry;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import com.lms.security.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.stereotype.Component;

/** Writes ledger events to the outbox (never straight to Kafka — docs/architecture.md). */
@Component
public class OutboxLedgerEventPublisher implements LedgerEventPublisher {

    private static final String TOPIC = "ledger.events";
    private static final String AGGREGATE = "journalEntry";
    private static final String PRODUCER = "ledger";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outbox;
    private final Serializer<Object> serializer;
    private final Clock clock;

    public OutboxLedgerEventPublisher(
            OutboxRepository outbox, Serializer<Object> serializer, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void entryPosted(JournalEntry entry) {
        String entryId = entry.id().toString();
        EventMeta meta = meta("ledger.entry-posted", entryId);
        LedgerEntryPosted event =
                LedgerEntryPosted.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setJournalEntryId(entryId)
                        .setSourceEventId(entry.sourceEventId().toString())
                        .setSourceEventType(entry.sourceEventType())
                        .setLoanId(entry.loanId())
                        .setCustomerId(entry.customerId())
                        .setCurrency(entry.currency().getCurrencyCode())
                        .setTotalDebitMinorUnits(entry.totalDebits().minorUnits())
                        .setTotalCreditMinorUnits(entry.totalCredits().minorUnits())
                        .setPostedAt(clock.instant())
                        .build();
        append(meta, entryId, event);
    }

    @Override
    public void entryReversed(JournalEntry reversalEntry, String reason) {
        String entryId = reversalEntry.id().toString();
        EventMeta meta = meta("ledger.entry-reversed", entryId);
        LedgerEntryReversed event =
                LedgerEntryReversed.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setJournalEntryId(entryId)
                        .setReversesEntryId(String.valueOf(reversalEntry.reversesEntryId()))
                        .setReason(reason)
                        .setReversedAt(clock.instant())
                        .build();
        append(meta, entryId, event);
    }

    @Override
    public void trialBalanceBroken(long diffMinorUnits, String currencyCode) {
        String reconRunId = Uuid7.generate(clock).toString();
        EventMeta meta = meta("ledger.recon-completed", reconRunId);
        LedgerReconCompleted event =
                LedgerReconCompleted.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setReconRunId(reconRunId)
                        .setCurrency(currencyCode)
                        .setDiffMinorUnits(diffMinorUnits)
                        .setBalanced(diffMinorUnits == 0L)
                        .setRunAt(clock.instant())
                        .build();
        append(meta, reconRunId, event);
    }

    @Override
    public void bankRailReconCompleted(
            java.util.UUID runId,
            String currencyCode,
            com.lms.ledger.domain.recon.ReconResult result) {
        String reconRunId = runId.toString();
        EventMeta meta = meta("ledger.recon-completed", reconRunId);
        LedgerReconCompleted event =
                LedgerReconCompleted.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setReconRunId(reconRunId)
                        .setCurrency(currencyCode)
                        .setDiffMinorUnits(0L)
                        .setBalanced(result.isClean())
                        .setRunAt(clock.instant())
                        .setReconType("BANK_RAIL")
                        .setMatched(
                                result.count(
                                        com.lms.ledger.domain.recon.ReconClassification.MATCHED))
                        .setMissingInLedger(
                                result.count(
                                        com.lms.ledger.domain.recon.ReconClassification
                                                .MISSING_IN_LEDGER))
                        .setMissingInBank(
                                result.count(
                                        com.lms.ledger.domain.recon.ReconClassification
                                                .MISSING_IN_BANK))
                        .setAmountMismatch(
                                result.count(
                                        com.lms.ledger.domain.recon.ReconClassification
                                                .AMOUNT_MISMATCH))
                        .setDuplicate(
                                result.count(
                                        com.lms.ledger.domain.recon.ReconClassification.DUPLICATE))
                        .build();
        append(meta, reconRunId, event);
    }

    private EventMeta meta(String eventType, String aggregateId) {
        return EventMeta.newEvent(
                clock,
                eventType,
                SCHEMA_VERSION,
                AGGREGATE,
                aggregateId,
                aggregateId,
                null,
                RequestContextHolder.current().traceId(),
                PRODUCER);
    }

    private void append(EventMeta meta, String aggregateId, SpecificRecord event) {
        byte[] payload = serializer.serialize(TOPIC, event);
        OutboxRecord row =
                new OutboxRecord(
                        Uuid7.generate(clock),
                        AGGREGATE,
                        aggregateId,
                        meta.eventType(),
                        meta.eventId(),
                        TOPIC,
                        aggregateId.getBytes(StandardCharsets.UTF_8),
                        payload,
                        EventHeaders.toHeaders(meta),
                        0,
                        null,
                        clock.instant(),
                        null);
        outbox.append(row);
    }
}
