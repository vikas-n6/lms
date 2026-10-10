package com.lms.money;

import java.util.Currency;

/** Thrown when an arithmetic or comparison operation mixes two different currencies. */
public final class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency left, Currency right) {
        super("Currency mismatch: " + left.getCurrencyCode() + " vs " + right.getCurrencyCode());
    }
}
