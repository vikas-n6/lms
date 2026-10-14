package com.lms.ledger.application.port.out;

import com.lms.ledger.domain.JournalEntry;

/**
 * Publishes ledger events onto {@code ledger.events} through the outbox (docs/architecture.md).
 * Keyed by journal entry id.
 */
public interface LedgerEventPublisher {

    void entryPosted(JournalEntry entry);

    void entryReversed(JournalEntry reversalEntry, String reason);

    /** Published by the reconciliation job when the trial balance does not net to zero. */
    void trialBalanceBroken(long diffMinorUnits, String currencyCode);
}
