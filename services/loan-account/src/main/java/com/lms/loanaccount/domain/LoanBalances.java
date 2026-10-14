package com.lms.loanaccount.domain;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A snapshot of a loan's balances for the read API (phase prompt task 2): outstanding principal,
 * unpaid interest, unpaid penal, the total due as of the business date, and the next unpaid due
 * date. Derived from the schedule lines — never a stored mutable counter.
 *
 * @param outstandingPrincipal unpaid scheduled principal
 * @param interestOutstanding unpaid scheduled interest
 * @param penalOutstanding unpaid penal
 * @param totalDueNow penal + interest + principal on instalments due on or before the business date
 * @param nextDueDate the earliest unpaid instalment's due date, if any
 */
public record LoanBalances(
        Money outstandingPrincipal,
        Money interestOutstanding,
        Money penalOutstanding,
        Money totalDueNow,
        Optional<LocalDate> nextDueDate) {

    public LoanBalances {
        Objects.requireNonNull(outstandingPrincipal, "outstandingPrincipal");
        Objects.requireNonNull(interestOutstanding, "interestOutstanding");
        Objects.requireNonNull(penalOutstanding, "penalOutstanding");
        Objects.requireNonNull(totalDueNow, "totalDueNow");
        Objects.requireNonNull(nextDueDate, "nextDueDate");
    }

    /** The total still owed over the life of the loan (principal + interest + penal). */
    public Money totalOutstanding() {
        return outstandingPrincipal.add(interestOutstanding).add(penalOutstanding);
    }
}
