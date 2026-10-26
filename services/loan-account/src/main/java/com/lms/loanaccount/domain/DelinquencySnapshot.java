package com.lms.loanaccount.domain;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A pure snapshot of a loan's overdue position on a business date (phase prompt task 1), derived
 * from the schedule lines by {@link Delinquency}. Two overdue notions are kept distinct:
 *
 * <ul>
 *   <li><b>DPD and overdue amounts</b> count instalments whose due date has passed and are not
 *       fully paid ({@code dueDate <= businessDate}), matching {@link Balances} "due now".
 *   <li><b>Penal base</b> counts only instalments past the <i>grace</i> window ({@code businessDate
 *       > dueDate + graceDays}, docs/domain-rules.md "Interest and penal accrual"), so penal starts
 *       only after the grace days.
 * </ul>
 *
 * @param daysPastDue days since the oldest unpaid past-due instalment (0 when nothing is overdue)
 * @param bucket the DPD bucket for {@code daysPastDue}
 * @param overdueInstalmentCount unpaid instalments with {@code dueDate <= businessDate}
 * @param overduePrincipal unpaid principal on those instalments
 * @param overdueInterest unpaid scheduled interest on those instalments
 * @param overduePenal unpaid penal on those instalments
 * @param penalBase principal + interest on instalments past the grace window (penal accrues on
 *     this)
 * @param oldestOverdueInstalment 1-based number of the oldest unpaid past-grace instalment, if any
 * @param oldestUnpaidDueDate the oldest unpaid past-due instalment's due date, if any
 */
public record DelinquencySnapshot(
        int daysPastDue,
        DpdBucket bucket,
        int overdueInstalmentCount,
        Money overduePrincipal,
        Money overdueInterest,
        Money overduePenal,
        Money penalBase,
        Optional<Integer> oldestOverdueInstalment,
        Optional<LocalDate> oldestUnpaidDueDate) {

    public DelinquencySnapshot {
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(overduePrincipal, "overduePrincipal");
        Objects.requireNonNull(overdueInterest, "overdueInterest");
        Objects.requireNonNull(overduePenal, "overduePenal");
        Objects.requireNonNull(penalBase, "penalBase");
        Objects.requireNonNull(oldestOverdueInstalment, "oldestOverdueInstalment");
        Objects.requireNonNull(oldestUnpaidDueDate, "oldestUnpaidDueDate");
        if (daysPastDue < 0) {
            throw new IllegalArgumentException("daysPastDue must be non-negative");
        }
    }

    /** penal + interest + principal overdue as of the business date. */
    public Money overdueTotal() {
        return overduePrincipal.add(overdueInterest).add(overduePenal);
    }

    /** True when nothing is overdue (DPD zero and no overdue amount). */
    public boolean isCurrent() {
        return daysPastDue == 0 && overdueTotal().isZero();
    }

    /** NPA at DPD 91 or more (docs/domain-rules.md "Loan state machine"). */
    public boolean isNpa() {
        return bucket == DpdBucket.NPA;
    }
}
