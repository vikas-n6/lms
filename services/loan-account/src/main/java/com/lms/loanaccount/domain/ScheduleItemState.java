package com.lms.loanaccount.domain;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The status of one schedule instalment (phase prompt task 2). Derived, not stored as truth: given
 * the scheduled and paid totals, the due date, the business date and the product grace days, the
 * status follows deterministically (docs/domain-rules.md). Precedence: fully paid wins; then
 * overdue (past due + grace, still owing); then partially paid; then due; else upcoming.
 */
public enum ScheduleItemState {
    UPCOMING,
    DUE,
    PARTIALLY_PAID,
    PAID,
    OVERDUE;

    public static ScheduleItemState of(
            Money scheduledTotal,
            Money paidTotal,
            LocalDate dueDate,
            LocalDate businessDate,
            int graceDays) {
        Objects.requireNonNull(scheduledTotal, "scheduledTotal");
        Objects.requireNonNull(paidTotal, "paidTotal");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(businessDate, "businessDate");
        if (graceDays < 0) {
            throw new IllegalArgumentException("graceDays must be non-negative");
        }
        if (paidTotal.compareTo(scheduledTotal) >= 0) {
            return PAID;
        }
        LocalDate overdueFrom = dueDate.plusDays(graceDays);
        if (businessDate.isAfter(overdueFrom)) {
            return OVERDUE;
        }
        if (paidTotal.isPositive()) {
            return PARTIALLY_PAID;
        }
        if (!businessDate.isBefore(dueDate)) {
            return DUE;
        }
        return UPCOMING;
    }
}
