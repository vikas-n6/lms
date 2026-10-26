package com.lms.loanaccount.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The last DPD position persisted for a loan (phase prompt task 1). Held so the daily job can (a)
 * be idempotent per {@code (loanId, businessDate)} — a re-run for a date already recorded does
 * nothing — and (b) publish {@code loan.dpd-updated} once more when DPD falls back to zero (the
 * previous day's DPD was positive). It is a derived cache, never the system of record for balances.
 *
 * @param loanId the loan
 * @param businessDate the date this position was computed for
 * @param daysPastDue DPD on that date
 * @param bucket the DPD bucket code
 */
public record DpdSnapshot(UUID loanId, LocalDate businessDate, int daysPastDue, String bucket) {

    public DpdSnapshot {
        Objects.requireNonNull(loanId, "loanId");
        Objects.requireNonNull(businessDate, "businessDate");
        Objects.requireNonNull(bucket, "bucket");
        if (daysPastDue < 0) {
            throw new IllegalArgumentException("daysPastDue must be non-negative");
        }
    }
}
