package com.lms.loanaccount.application.port.out;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Persistence port for daily accruals (phase prompt task 2). Interest accrual is idempotent per
 * {@code (loanId, businessDate)} via a unique constraint: {@link #recordInterestAccrual} returns
 * false if the day was already accrued, so running the job twice posts once (docs/domain-rules.md
 * "Interest and penal accrual").
 */
public interface AccrualRepository {

    /**
     * Record one day's interest accrual. Returns {@code true} if inserted, {@code false} if the
     * {@code (loanId, businessDate)} accrual already existed (duplicate, skipped).
     */
    boolean recordInterestAccrual(
            UUID loanId, LocalDate businessDate, int instalmentNumber, Money amount);

    /**
     * Record one day's penal accrual, idempotent per {@code (loanId, businessDate)} for {@code
     * kind=PENAL}. Returns {@code true} if inserted, {@code false} if already accrued that day.
     */
    boolean recordPenalAccrual(
            UUID loanId, LocalDate businessDate, int instalmentNumber, Money amount);

    /**
     * Record that an instalment's pre-due reminder was sent, idempotent per {@code (loanId,
     * businessDate)} for {@code kind=DUE_SOON}. Returns {@code true} if inserted (first time that
     * business date), {@code false} if already sent, so a re-run publishes no second reminder.
     */
    boolean recordDueSoonSent(UUID loanId, LocalDate businessDate, int instalmentNumber);

    /** Total interest accrued over the life of the loan so far. */
    Money totalInterestAccrued(UUID loanId);

    /** Total penal accrued over the life of the loan so far. */
    Money totalPenalAccrued(UUID loanId);
}
