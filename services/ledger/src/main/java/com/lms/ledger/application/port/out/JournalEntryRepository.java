package com.lms.ledger.application.port.out;

import com.lms.ledger.domain.JournalEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for journal entries and their postings (jOOQ-backed, ADR 0007). Entries are
 * immutable and append-only; there is no update or delete (docs/domain-rules.md "Ledger
 * invariants"). Idempotency is enforced by {@link #existsForSourceEvent} plus a unique index on
 * {@code source_event_id}.
 */
public interface JournalEntryRepository {

    boolean existsForSourceEvent(UUID sourceEventId);

    /** True if some entry already reverses {@code entryId} (so it cannot be reversed again). */
    boolean isReversed(UUID entryId);

    void save(JournalEntry entry);

    Optional<JournalEntry> findById(UUID id);

    /** Entries filtered by any of loan id, posting date and account code, newest first. */
    List<JournalEntry> find(
            Optional<String> loanId, Optional<LocalDate> date, Optional<String> accountCode);

    /** All entries posted on or before {@code asOf} (for the trial balance / account balances). */
    List<JournalEntry> findPostedUpTo(LocalDate asOf);

    /** All entries, for the full trial balance and the reconciliation job. */
    List<JournalEntry> findAll();
}
