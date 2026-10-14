package com.lms.ledger.domain;

import com.lms.money.Money;
import java.util.Objects;

/**
 * A single posting line: a positive amount on the debit or credit side of an account, optionally
 * tagged with the loan and customer dimensions (docs/domain-rules.md "Ledger invariants").
 *
 * @param account the account posted to
 * @param side debit or credit
 * @param amount a positive amount in the entry's currency
 * @param loanId loan dimension (nullable)
 * @param customerId customer dimension (nullable)
 */
public record Posting(
        AccountCode account, PostingSide side, Money amount, String loanId, String customerId) {

    public Posting {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("posting amount must be positive");
        }
    }

    public static Posting debit(
            AccountCode account, Money amount, String loanId, String customerId) {
        return new Posting(account, PostingSide.DEBIT, amount, loanId, customerId);
    }

    public static Posting credit(
            AccountCode account, Money amount, String loanId, String customerId) {
        return new Posting(account, PostingSide.CREDIT, amount, loanId, customerId);
    }

    /** A copy with the side flipped (used when building a reversing entry). */
    public Posting reversed() {
        return new Posting(account, side.opposite(), amount, loanId, customerId);
    }
}
