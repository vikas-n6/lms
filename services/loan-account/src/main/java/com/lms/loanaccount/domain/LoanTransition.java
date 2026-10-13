package com.lms.loanaccount.domain;

/**
 * A trigger that may move a loan between {@link LoanState} values (docs/domain-rules.md "Loan state
 * machine"). Each trigger carries the facts its guard needs, pre-evaluated by the application layer
 * (money comparisons, overdue totals, DPD), so {@link LoanStateMachine} stays a pure function with
 * no I/O. A trigger whose guard fails yields an illegal transition and leaves the state unchanged.
 */
public sealed interface LoanTransition
        permits LoanTransition.DraftSchedule,
                LoanTransition.ConfirmDisbursement,
                LoanTransition.CancelDisbursement,
                LoanTransition.MarkDelinquent,
                LoanTransition.ClearOverdue,
                LoanTransition.PayInFull,
                LoanTransition.Foreclose,
                LoanTransition.WriteOff {

    /**
     * The provisional schedule has been drafted. Guard: it exists (Created → PendingDisbursal).
     *
     * @param provisionalScheduleExists whether a provisional schedule was drafted
     */
    record DraftSchedule(boolean provisionalScheduleExists) implements LoanTransition {}

    /**
     * Disbursement succeeded. Guard: the disbursed net equals principal − fee − GST
     * (PendingDisbursal → Active).
     *
     * @param amountMatches whether the disbursed net matches the loan's net
     */
    record ConfirmDisbursement(boolean amountMatches) implements LoanTransition {}

    /**
     * Disbursement failed after retries, or the offer expired. Guard: compensation completed
     * (PendingDisbursal → Cancelled).
     *
     * @param compensationCompleted whether any in-transit posting has been compensated
     */
    record CancelDisbursement(boolean compensationCompleted) implements LoanTransition {}

    /**
     * DPD exceeded the grace period. Guard: at least one overdue instalment (Active → Delinquent).
     *
     * @param overdueInstalmentCount number of overdue instalments
     */
    record MarkDelinquent(int overdueInstalmentCount) implements LoanTransition {}

    /**
     * Overdue amounts cleared. Guard: the overdue total is zero (Delinquent → Active).
     *
     * @param overdueTotalZero whether no overdue amount remains
     */
    record ClearOverdue(boolean overdueTotalZero) implements LoanTransition {}

    /**
     * The outstanding total reached zero. Guard: all instalments paid (Active|Delinquent → Closed).
     *
     * @param outstandingZero whether nothing is outstanding
     */
    record PayInFull(boolean outstandingZero) implements LoanTransition {}

    /**
     * A full foreclosure payment was allocated. Guard: amount at least the foreclosure quote
     * (Active|Delinquent → Foreclosed).
     *
     * @param amountCoversQuote whether the payment covers the foreclosure quote
     */
    record Foreclose(boolean amountCoversQuote) implements LoanTransition {}

    /**
     * An admin write-off command. Guard: DPD at least the configured write-off threshold (default
     * 180, docs/domain-rules.md) and an approval record exists (Delinquent → WrittenOff). The
     * threshold comparison is done in the application layer against versioned config, so no
     * business parameter is hard-coded in the domain (CLAUDE.md global do-not list).
     *
     * @param dpdThresholdMet whether DPD has reached the configured write-off threshold
     * @param approvalRecordPresent whether an approval record exists
     */
    record WriteOff(boolean dpdThresholdMet, boolean approvalRecordPresent)
            implements LoanTransition {}
}
