package com.lms.ledger.domain.recon;

import com.lms.money.Money;
import java.util.Objects;

/**
 * One line to reconcile (phase prompt task 6): an external line from the bank statement or rail
 * settlement report, or a ledger posting on account 1001. Matched by {@code reference} (UTR or
 * provider reference) and {@code amount}.
 *
 * @param reference the UTR / provider reference used to match
 * @param amount the line amount (bank/rail credit-debit on the operating account)
 */
public record ReconLine(String reference, Money amount) {

    public ReconLine {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(amount, "amount");
    }
}
