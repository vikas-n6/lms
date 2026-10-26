package com.lms.loanaccount.application.port.out;

import com.lms.loanaccount.domain.AllocationResult;
import com.lms.loanaccount.domain.DelinquencySnapshot;
import com.lms.loanaccount.domain.Loan;
import com.lms.money.Money;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Publishes loan lifecycle events onto {@code loan.events} (through the outbox, never straight to
 * Kafka — docs/architecture.md "Kafka rules"). Keyed by loan id so events for one loan stay
 * ordered. The {@code causationEventId} links each published event to the event that caused it.
 */
public interface LoanEventPublisher {

    void loanCreated(Loan loan, UUID causationEventId);

    void loanActivated(
            Loan loan, LocalDate firstDueDate, LocalDate maturityDate, UUID causationEventId);

    void interestAccrued(
            UUID loanId,
            String loanNumber,
            LocalDate businessDate,
            int instalmentNumber,
            Money amount);

    /** One day's penal interest recognised on the overdue base (phase prompt task 1). */
    void penalAccrued(
            UUID loanId,
            String loanNumber,
            LocalDate businessDate,
            Money amount,
            Money overdueBase);

    /**
     * The daily DPD position (phase prompt task 1): published for every loan with DPD above zero,
     * plus once when it returns to zero.
     */
    void dpdUpdated(
            UUID loanId, String loanNumber, LocalDate businessDate, DelinquencySnapshot snapshot);

    /** An instalment falls due within the pre-due window (phase prompt task 1). */
    void installmentDueSoon(
            UUID loanId,
            String loanNumber,
            int instalmentNumber,
            LocalDate dueDate,
            Money amountDue);

    /** A loan written off after two-person approval (phase prompt task 2). */
    void writtenOff(
            Loan loan,
            String requestId,
            Money principal,
            Money interest,
            Money penal,
            String reason,
            String requestedBy,
            String approvedBy,
            UUID causationEventId);

    void repaymentAllocated(
            UUID loanId,
            String loanNumber,
            String repaymentId,
            AllocationResult result,
            UUID causationEventId);

    void repaymentReversed(
            UUID loanId,
            String loanNumber,
            String repaymentId,
            Money penal,
            Money interest,
            Money principal,
            UUID causationEventId);

    void loanForeclosed(
            Loan loan, String repaymentId, Money foreclosureAmount, UUID causationEventId);

    void loanClosed(Loan loan, String reason, UUID causationEventId);

    void loanCancelled(Loan loan, String reason, UUID causationEventId);
}
