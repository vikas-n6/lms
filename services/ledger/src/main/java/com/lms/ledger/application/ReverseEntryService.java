package com.lms.ledger.application;

import com.lms.events.Uuid7;
import com.lms.ledger.application.port.out.JournalEntryRepository;
import com.lms.ledger.application.port.out.LedgerEventPublisher;
import com.lms.ledger.domain.JournalEntry;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Reverses a journal entry by posting a mirrored entry with {@code reversesEntryId} set (admin,
 * reason required — docs/domain-rules.md "Ledger invariants" rule 1). The original is never
 * mutated, and reversing the same entry twice is rejected.
 */
public class ReverseEntryService {

    private final JournalEntryRepository entries;
    private final LedgerEventPublisher publisher;
    private final Clock clock;

    public ReverseEntryService(
            JournalEntryRepository entries, LedgerEventPublisher publisher, Clock clock) {
        this.entries = Objects.requireNonNull(entries);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public JournalEntry reverse(UUID entryId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a reversal reason is required");
        }
        JournalEntry original =
                entries.findById(entryId).orElseThrow(() -> new EntryNotFoundException(entryId));
        if (original.reversesEntryId() != null) {
            throw new IllegalStateException(
                    "a reversal entry cannot itself be reversed: " + entryId);
        }
        if (entries.isReversed(entryId)) {
            throw new EntryAlreadyReversedException(entryId);
        }
        JournalEntry reversal =
                original.reverse(Uuid7.generate(clock), Uuid7.generate(clock), clock.instant());
        entries.save(reversal);
        publisher.entryReversed(reversal, reason);
        return reversal;
    }
}
