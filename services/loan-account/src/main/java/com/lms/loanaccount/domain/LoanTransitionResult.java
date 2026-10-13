package com.lms.loanaccount.domain;

/**
 * The typed result of applying a {@link LoanTransition} to a {@link LoanState}: either a legal move
 * to a new state, or an illegal transition that is rejected and audited, never ignored
 * (docs/domain-rules.md "Loan state machine"). A guard that fails is also {@link Illegal}, carrying
 * a reason so the rejection can be explained in {@code loan_state_history}.
 */
public sealed interface LoanTransitionResult
        permits LoanTransitionResult.Moved, LoanTransitionResult.Illegal {

    record Moved(LoanState from, LoanTransition trigger, LoanState to)
            implements LoanTransitionResult {}

    record Illegal(LoanState from, LoanTransition trigger, String reason)
            implements LoanTransitionResult {}

    default boolean isLegal() {
        return this instanceof Moved;
    }
}
