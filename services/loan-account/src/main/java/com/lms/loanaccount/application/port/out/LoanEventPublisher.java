package com.lms.loanaccount.application.port.out;

import com.lms.loanaccount.domain.AllocationResult;
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

    void repaymentAllocated(
            UUID loanId,
            String loanNumber,
            String repaymentId,
            AllocationResult result,
            UUID causationEventId);

    void loanClosed(Loan loan, String reason, UUID causationEventId);

    void loanCancelled(Loan loan, String reason, UUID causationEventId);
}
