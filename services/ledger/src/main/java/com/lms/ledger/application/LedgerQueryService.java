package com.lms.ledger.application;

import com.lms.ledger.application.port.out.JournalEntryRepository;
import com.lms.ledger.domain.AccountCode;
import com.lms.ledger.domain.JournalEntry;
import com.lms.ledger.domain.TrialBalance;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model for the ledger APIs (phase prompt task 3): entries with filters, entry detail, account
 * balance as of a date, the trial balance, and a per-loan sub-ledger. Balances are always derived
 * from postings (docs/domain-rules.md "Ledger invariants" rule 4).
 */
public class LedgerQueryService {

    private final JournalEntryRepository entries;

    public LedgerQueryService(JournalEntryRepository entries) {
        this.entries = Objects.requireNonNull(entries);
    }

    public List<JournalEntry> entries(
            Optional<String> loanId, Optional<LocalDate> date, Optional<String> accountCode) {
        return entries.find(loanId, date, accountCode);
    }

    public Optional<JournalEntry> entry(UUID id) {
        return entries.findById(id);
    }

    public List<JournalEntry> subLedger(String loanId) {
        return entries.find(Optional.of(loanId), Optional.empty(), Optional.empty());
    }

    public TrialBalance trialBalance() {
        return TrialBalance.of(entries.findAll());
    }

    public TrialBalance trialBalanceAsOf(LocalDate asOf) {
        return TrialBalance.of(entries.findPostedUpTo(asOf));
    }

    /** The normal-side balance of an account as of a date (minor units). */
    public long accountBalanceAsOf(AccountCode account, LocalDate asOf) {
        return trialBalanceAsOf(asOf).normalBalanceOf(account);
    }
}
