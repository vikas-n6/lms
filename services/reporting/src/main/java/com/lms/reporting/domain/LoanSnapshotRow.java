package com.lms.reporting.domain;

import com.lms.money.Money;
import java.util.UUID;

/**
 * A denormalised loan read row: the current projection of one loan built purely from the loan and
 * disbursement events (ADR 0013 — reporting never reads the loan database). {@code disbursedAmount}
 * and {@code disbursalMonth} are null until {@code disbursement.succeeded}/{@code loan.activated}.
 * The three outstanding components are bigint minor units in the same currency.
 */
public record LoanSnapshotRow(
        UUID loanId,
        String customerId,
        int productVersion,
        String state,
        int dpd,
        DpdBucketCategory bucket,
        Money principalOutstanding,
        Money interestOutstanding,
        Money penalOutstanding,
        Money disbursedAmount,
        String disbursalMonth) {

    /** Active loans still carry a balance and are not in a terminal state. */
    public boolean isActive() {
        return switch (state) {
            case "Active", "Delinquent", "PendingDisbursal" -> true;
            default -> false;
        };
    }

    public Money totalOutstanding() {
        return principalOutstanding.add(interestOutstanding).add(penalOutstanding);
    }

    public boolean isDisbursed() {
        return disbursalMonth != null;
    }
}
