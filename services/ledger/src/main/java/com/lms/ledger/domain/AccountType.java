package com.lms.ledger.domain;

/**
 * The accounting type of a ledger account (docs/domain-rules.md "Chart of accounts"). The normal
 * balance side follows from the type: assets and expenses are debit-normal, liabilities and income
 * are credit-normal.
 */
public enum AccountType {
    ASSET(PostingSide.DEBIT),
    LIABILITY(PostingSide.CREDIT),
    INCOME(PostingSide.CREDIT),
    EXPENSE(PostingSide.DEBIT);

    private final PostingSide normalSide;

    AccountType(PostingSide normalSide) {
        this.normalSide = normalSide;
    }

    public PostingSide normalSide() {
        return normalSide;
    }
}
