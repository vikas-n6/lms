package com.lms.loanaccount.domain;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A persisted schedule instalment with the amounts paid per component and any penal accrued on it
 * (phase prompt task 2). The scheduled principal/interest come from {@link
 * com.lms.schedule.ScheduleItem}; paid components and penal accumulate over the life of the loan.
 * Immutable; applying a payment returns a new line.
 *
 * @param loanId owning loan
 * @param instalmentNumber 1-based
 * @param dueDate business due date
 * @param principal scheduled principal portion
 * @param interest scheduled interest portion
 * @param paidPrincipal principal paid so far
 * @param paidInterest interest paid so far
 * @param penalAccrued penal accrued on this instalment so far
 * @param penalPaid penal paid so far
 * @param provisional true for the indicative pre-disbursement schedule
 */
public record LoanScheduleLine(
        UUID loanId,
        int instalmentNumber,
        LocalDate dueDate,
        Money principal,
        Money interest,
        Money paidPrincipal,
        Money paidInterest,
        Money penalAccrued,
        Money penalPaid,
        boolean provisional) {

    public LoanScheduleLine {
        Objects.requireNonNull(loanId, "loanId");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(interest, "interest");
        Objects.requireNonNull(paidPrincipal, "paidPrincipal");
        Objects.requireNonNull(paidInterest, "paidInterest");
        Objects.requireNonNull(penalAccrued, "penalAccrued");
        Objects.requireNonNull(penalPaid, "penalPaid");
        if (instalmentNumber <= 0) {
            throw new IllegalArgumentException("instalmentNumber must be positive");
        }
    }

    /** The total scheduled instalment (principal + interest), excluding penal. */
    public Money scheduledTotal() {
        return principal.add(interest);
    }

    /** The total paid toward principal + interest, excluding penal. */
    public Money paidTotal() {
        return paidPrincipal.add(paidInterest);
    }

    public Money principalOutstanding() {
        return principal.subtract(paidPrincipal);
    }

    public Money interestOutstanding() {
        return interest.subtract(paidInterest);
    }

    public Money penalOutstanding() {
        return penalAccrued.subtract(penalPaid);
    }

    /** The outstanding due as an {@link InstalmentDue} for the allocation waterfall. */
    public InstalmentDue toDue() {
        return new InstalmentDue(
                instalmentNumber,
                penalOutstanding(),
                interestOutstanding(),
                principalOutstanding());
    }

    /** True when principal, interest and penal are all fully paid. */
    public boolean isFullyPaid() {
        return principalOutstanding().isZero()
                && interestOutstanding().isZero()
                && penalOutstanding().isZero();
    }

    /** A copy with additional amounts applied to the components (from an allocation). */
    public LoanScheduleLine applyPayment(Money toPenal, Money toInterest, Money toPrincipal) {
        return new LoanScheduleLine(
                loanId,
                instalmentNumber,
                dueDate,
                principal,
                interest,
                paidPrincipal.add(toPrincipal),
                paidInterest.add(toInterest),
                penalAccrued,
                penalPaid.add(toPenal),
                provisional);
    }

    /**
     * A copy with the given amounts removed from the paid components, reopening the instalment when
     * a repayment is reversed (docs/domain-rules.md "Repayment allocation waterfall" rule 5). The
     * amounts must not exceed what was paid (the caller reverses exactly a prior allocation).
     */
    public LoanScheduleLine reversePayment(
            Money fromPenal, Money fromInterest, Money fromPrincipal) {
        return new LoanScheduleLine(
                loanId,
                instalmentNumber,
                dueDate,
                principal,
                interest,
                paidPrincipal.subtract(fromPrincipal),
                paidInterest.subtract(fromInterest),
                penalAccrued,
                penalPaid.subtract(fromPenal),
                provisional);
    }

    /** A copy with more penal accrued on this instalment. */
    public LoanScheduleLine accruePenal(Money amount) {
        return new LoanScheduleLine(
                loanId,
                instalmentNumber,
                dueDate,
                principal,
                interest,
                paidPrincipal,
                paidInterest,
                penalAccrued.add(amount),
                penalPaid,
                provisional);
    }
}
