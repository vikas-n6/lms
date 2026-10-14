package com.lms.ledger.domain;

import java.util.Currency;

/** Raised when a journal entry's debits do not equal its credits (docs/domain-rules.md). */
public class UnbalancedEntryException extends RuntimeException {

    public UnbalancedEntryException(long debitsMinor, long creditsMinor, Currency currency) {
        super(
                "unbalanced journal entry: debits="
                        + debitsMinor
                        + " credits="
                        + creditsMinor
                        + " "
                        + currency.getCurrencyCode());
    }
}
