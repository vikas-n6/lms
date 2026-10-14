package com.lms.ledger.domain;

/** The side of a posting. Every journal entry's debits must equal its credits per currency. */
public enum PostingSide {
    DEBIT,
    CREDIT;

    public PostingSide opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
