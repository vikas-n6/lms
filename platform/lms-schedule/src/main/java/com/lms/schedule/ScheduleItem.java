package com.lms.schedule;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One instalment line of an amortisation schedule (docs/domain-rules.md "Schedule engine").
 *
 * @param instalmentNumber 1-based position
 * @param dueDate business due date (month-end clamped)
 * @param amount total due = {@code principal + interest}
 * @param principal principal portion retiring the outstanding balance
 * @param interest interest portion for the period
 * @param outstandingAfter principal balance remaining after this instalment (0 on the last)
 */
public record ScheduleItem(
        int instalmentNumber,
        LocalDate dueDate,
        Money amount,
        Money principal,
        Money interest,
        Money outstandingAfter) {

    public ScheduleItem {
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(interest, "interest");
        Objects.requireNonNull(outstandingAfter, "outstandingAfter");
        if (instalmentNumber <= 0) {
            throw new IllegalArgumentException("instalmentNumber must be positive");
        }
    }
}
