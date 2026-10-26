package com.lms.ledger.application.port.out;

import com.lms.ledger.domain.JournalEntry;
import com.lms.ledger.domain.recon.ReconResult;
import java.util.UUID;

/**
 * Publishes ledger events onto {@code ledger.events} through the outbox (docs/architecture.md).
 * Keyed by journal entry id.
 */
public interface LedgerEventPublisher {

    void entryPosted(JournalEntry entry);

    void entryReversed(JournalEntry reversalEntry, String reason);

    /**
     * Published by the integrity job when the trial balance does not net to zero (TRIAL_BALANCE).
     */
    void trialBalanceBroken(long diffMinorUnits, String currencyCode);

    /** Published when a bank/rail reconciliation run completes (BANK_RAIL, phase prompt task 6). */
    void bankRailReconCompleted(UUID runId, String currencyCode, ReconResult result);
}
