package com.lms.ledger.application;

import com.lms.ledger.application.port.out.JournalEntryRepository;
import com.lms.ledger.application.port.out.LedgerEventPublisher;
import com.lms.ledger.domain.TrialBalance;
import java.util.Objects;

/**
 * The scheduled integrity check (phase prompt task 3): the trial balance must net to zero. Returns
 * the diff (exported as the metric {@code ledger_trial_balance_diff_minor_units}) and publishes an
 * alert event when it is non-zero (docs/domain-rules.md "Ledger invariants" rule 5).
 */
public class ReconciliationService {

    private final JournalEntryRepository entries;
    private final LedgerEventPublisher publisher;
    private final String currencyCode;

    public ReconciliationService(
            JournalEntryRepository entries, LedgerEventPublisher publisher, String currencyCode) {
        this.entries = Objects.requireNonNull(entries);
        this.publisher = Objects.requireNonNull(publisher);
        this.currencyCode = Objects.requireNonNull(currencyCode);
    }

    /** Run the check; returns the trial-balance diff in minor units (0 when healthy). */
    public long reconcile() {
        TrialBalance trialBalance = TrialBalance.of(entries.findAll());
        long diff = trialBalance.diffMinorUnits();
        if (diff != 0L) {
            publisher.trialBalanceBroken(diff, currencyCode);
        }
        return diff;
    }
}
