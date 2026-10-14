package com.lms.ledger.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A trial balance derived by summing postings — never a stored counter (docs/domain-rules.md
 * "Ledger invariants" rules 4–5). Per account it holds the signed balance in minor units, oriented
 * to the account's normal side (positive = normal-side balance). The whole book always nets to
 * zero; {@link #diffMinorUnits()} is the metric {@code ledger_trial_balance_diff_minor_units} and
 * must be zero.
 */
public record TrialBalance(
        long totalDebitMinorUnits,
        long totalCreditMinorUnits,
        Map<AccountCode, Long> normalBalances) {

    public TrialBalance {
        normalBalances = Map.copyOf(normalBalances);
    }

    /** Sum the postings of the given entries into a trial balance. */
    public static TrialBalance of(List<JournalEntry> entries) {
        Map<AccountCode, Long> normal = new EnumMap<>(AccountCode.class);
        long debits = 0L;
        long credits = 0L;
        for (JournalEntry entry : entries) {
            for (Posting posting : entry.postings()) {
                long amount = posting.amount().minorUnits();
                if (posting.side() == PostingSide.DEBIT) {
                    debits += amount;
                } else {
                    credits += amount;
                }
                long signed = posting.side() == posting.account().normalSide() ? amount : -amount;
                normal.merge(posting.account(), signed, Long::sum);
            }
        }
        return new TrialBalance(debits, credits, normal);
    }

    /** Total debits minus total credits; zero when the book balances. */
    public long diffMinorUnits() {
        return totalDebitMinorUnits - totalCreditMinorUnits;
    }

    public boolean balanced() {
        return diffMinorUnits() == 0L;
    }

    /** The normal-side balance (minor units) of one account, 0 if it has no postings. */
    public long normalBalanceOf(AccountCode account) {
        return normalBalances.getOrDefault(account, 0L);
    }
}
