package com.lms.loanaccount.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Days-past-due buckets (docs/domain-rules.md "Loan state machine"). DPD counts days since the
 * oldest unpaid due date; the buckets are the fixed classification used across the system (not a
 * per-product parameter), so they live in the domain model.
 */
public enum DpdBucket {
    CURRENT,
    DPD_1_30,
    DPD_31_60,
    DPD_61_90,
    NPA;

    /** The bucket for a raw days-past-due count. */
    public static DpdBucket of(int daysPastDue) {
        if (daysPastDue <= 0) {
            return CURRENT;
        }
        if (daysPastDue <= 30) {
            return DPD_1_30;
        }
        if (daysPastDue <= 60) {
            return DPD_31_60;
        }
        if (daysPastDue <= 90) {
            return DPD_61_90;
        }
        return NPA;
    }

    /**
     * Days past due = days since the oldest unpaid due date (0 if nothing is overdue or no due date
     * has passed).
     */
    public static int daysPastDue(Optional<LocalDate> oldestUnpaidDueDate, LocalDate businessDate) {
        return oldestUnpaidDueDate
                .map(due -> (int) Math.max(0, ChronoUnit.DAYS.between(due, businessDate)))
                .orElse(0);
    }

    /** The bucket for the oldest unpaid due date as of {@code businessDate}. */
    public static DpdBucket forOldestUnpaid(
            Optional<LocalDate> oldestUnpaidDueDate, LocalDate businessDate) {
        return of(daysPastDue(oldestUnpaidDueDate, businessDate));
    }
}
