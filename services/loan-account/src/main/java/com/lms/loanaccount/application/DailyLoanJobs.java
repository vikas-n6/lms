package com.lms.loanaccount.application;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Runs the loan-account daily jobs in the order the phase prompt fixes (task 1), each idempotent
 * per {@code (loanId, businessDate)}:
 *
 * <ol>
 *   <li>mark instalments DUE / OVERDUE — <b>derived</b> from the schedule, business date and grace
 *       days ({@link com.lms.loanaccount.domain.ScheduleItemState}); there is no stored status to
 *       mutate, so this step is a no-op here and cannot drift (phase prompt "Do not" / balances are
 *       never stored);
 *   <li>interest accrual ({@link AccrueInterestService});
 *   <li>penal accrual ({@link PenalAccrualService});
 *   <li>DPD computation, Active↔Delinquent transitions and {@code loan.dpd-updated} ({@link
 *       DpdService}).
 * </ol>
 *
 * <p>The pre-due reminder ({@link DueSoonService}) runs for the same business date. All jobs take
 * the business date as a parameter; the scheduled trigger supplies {@code
 * BusinessCalendar.today()}.
 */
public class DailyLoanJobs {

    private final AccrueInterestService interest;
    private final PenalAccrualService penal;
    private final DpdService dpd;
    private final DueSoonService dueSoon;

    public DailyLoanJobs(
            AccrueInterestService interest,
            PenalAccrualService penal,
            DpdService dpd,
            DueSoonService dueSoon) {
        this.interest = Objects.requireNonNull(interest);
        this.penal = Objects.requireNonNull(penal);
        this.dpd = Objects.requireNonNull(dpd);
        this.dueSoon = Objects.requireNonNull(dueSoon);
    }

    public Result runFor(LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate");
        int interestRecorded = interest.runFor(businessDate);
        int penalRecorded = penal.runFor(businessDate);
        int dpdProcessed = dpd.runFor(businessDate);
        int dueSoonSent = dueSoon.runFor(businessDate);
        return new Result(interestRecorded, penalRecorded, dpdProcessed, dueSoonSent);
    }

    /** Counts recorded by each step, for the admin job-run response and logs. */
    public record Result(int interest, int penal, int dpd, int dueSoon) {}
}
