package com.lms.loanaccount.domain;

import com.lms.money.Money;
import com.lms.schedule.ScheduleItem;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Daily interest-income recognition (docs/domain-rules.md "Interest and penal accrual"): each
 * instalment's scheduled interest is spread evenly across the days of its period, and the last day
 * of the period takes the rounding remainder. Pure date and {@link Money} maths — no clock, no I/O.
 *
 * <p>The period of instalment {@code k} runs from the previous due date (the disbursement date for
 * {@code k == 1}), exclusive, to its own due date, inclusive. A business date on or before
 * disbursement, or after the final due date, recognises nothing. Because the spread sums back
 * exactly to the scheduled interest, recognising every day of a 12-month loan reproduces the
 * scheduled interest per instalment to the minor unit.
 */
public final class InterestAccrual {

    private InterestAccrual() {}

    /**
     * The interest recognised on {@code businessDate}, or empty if the date is outside the loan's
     * accrual window.
     */
    public static Optional<DailyInterest> recognise(
            List<ScheduleItem> items, LocalDate disbursementDate, LocalDate businessDate) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(disbursementDate, "disbursementDate");
        Objects.requireNonNull(businessDate, "businessDate");

        LocalDate periodStart = disbursementDate;
        for (ScheduleItem item : items) {
            LocalDate dueDate = item.dueDate();
            if (businessDate.isAfter(periodStart) && !businessDate.isAfter(dueDate)) {
                int daysInPeriod = (int) ChronoUnit.DAYS.between(periodStart, dueDate);
                int dayOfPeriod =
                        (int) ChronoUnit.DAYS.between(periodStart, businessDate); // 1-based
                Money[] spread = item.interest().spreadWithRemainderLast(daysInPeriod);
                return Optional.of(
                        new DailyInterest(item.instalmentNumber(), spread[dayOfPeriod - 1]));
            }
            periodStart = dueDate;
        }
        return Optional.empty();
    }
}
