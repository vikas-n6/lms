package com.lms.ledger.application;

import com.lms.ledger.application.port.out.ExternalReconFeed;
import com.lms.ledger.application.port.out.JournalEntryRepository;
import com.lms.ledger.application.port.out.LedgerEventPublisher;
import com.lms.ledger.application.port.out.ReconRunRepository;
import com.lms.ledger.domain.recon.ReconLine;
import com.lms.ledger.domain.recon.ReconMatcher;
import com.lms.ledger.domain.recon.ReconResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The daily bank/rail reconciliation (phase prompt task 6). It pulls the bank statement and the
 * rail settlement report for a date, matches them against the ledger's money-movement references
 * (the account-1001 postings tagged with a UTR / provider reference) via the pure {@link
 * ReconMatcher}, persists the run and its items, and publishes {@code ledger.recon-completed}
 * (BANK_RAIL).
 */
public class BankRailReconciliationService {

    private final ExternalReconFeed feed;
    private final JournalEntryRepository entries;
    private final ReconRunRepository runs;
    private final LedgerEventPublisher publisher;
    private final String currencyCode;
    private final Clock clock;

    public BankRailReconciliationService(
            ExternalReconFeed feed,
            JournalEntryRepository entries,
            ReconRunRepository runs,
            LedgerEventPublisher publisher,
            String currencyCode,
            Clock clock) {
        this.feed = Objects.requireNonNull(feed);
        this.entries = Objects.requireNonNull(entries);
        this.runs = Objects.requireNonNull(runs);
        this.publisher = Objects.requireNonNull(publisher);
        this.currencyCode = Objects.requireNonNull(currencyCode);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Reconcile {@code date}; returns the persisted run id. */
    public UUID reconcile(LocalDate date) {
        Objects.requireNonNull(date, "date");
        List<ReconLine> external = new ArrayList<>();
        external.addAll(feed.bankStatement(date));
        external.addAll(feed.railReport(date));
        List<ReconLine> ledger = entries.reconLines(date);

        ReconResult result = ReconMatcher.match(external, ledger);
        UUID runId = runs.saveRun(date, currencyCode, result, clock.instant());
        publisher.bankRailReconCompleted(runId, currencyCode, result);
        return runId;
    }

    /** Resolve a recon item with a note (admin). */
    public void resolve(UUID itemId, String note) {
        runs.resolveItem(itemId, note);
    }
}
