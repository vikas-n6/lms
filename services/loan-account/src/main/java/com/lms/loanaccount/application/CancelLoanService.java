package com.lms.loanaccount.application;

import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanState;
import com.lms.loanaccount.domain.LoanStateMachine;
import com.lms.loanaccount.domain.LoanTransition.CancelDisbursement;
import com.lms.loanaccount.domain.LoanTransitionResult;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles {@code disbursement.failed} (and offer expiry): cancels a {@link
 * LoanState.PendingDisbursal} loan once compensation is complete, moving it to {@link
 * LoanState.Cancelled} and publishing {@code loan.cancelled} (docs/domain-rules.md "Loan state
 * machine"). Idempotent: a loan already Cancelled is returned unchanged.
 */
public class CancelLoanService {

    private final LoanRepository loans;
    private final StateHistoryRepository history;
    private final LoanEventPublisher publisher;
    private final Clock clock;

    public CancelLoanService(
            LoanRepository loans,
            StateHistoryRepository history,
            LoanEventPublisher publisher,
            Clock clock) {
        this.loans = Objects.requireNonNull(loans);
        this.history = Objects.requireNonNull(history);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public Loan cancel(UUID loanId, String reason, UUID sourceEventId) {
        Loan loan = loans.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        if (loan.state() instanceof LoanState.Cancelled) {
            return loan; // idempotent
        }
        // Compensation of any in-transit posting is handled by the ledger reversing on
        // disbursement.failed; by the time we cancel here it is considered complete.
        LoanTransitionResult result =
                LoanStateMachine.transition(loan.state(), new CancelDisbursement(true));
        if (!(result instanceof LoanTransitionResult.Moved moved)) {
            LoanTransitionResult.Illegal illegal = (LoanTransitionResult.Illegal) result;
            history.append(
                    loanId,
                    loan.state(),
                    loan.state(),
                    "disbursement.failed",
                    sourceEventId,
                    false);
            throw new LoanTransitionRejectedException(illegal);
        }
        Loan cancelled = loan.withState(moved.to(), clock.instant());
        loans.update(cancelled);
        history.append(
                loanId, moved.from(), moved.to(), "disbursement.failed", sourceEventId, true);
        publisher.loanCancelled(cancelled, reason, sourceEventId);
        return cancelled;
    }
}
