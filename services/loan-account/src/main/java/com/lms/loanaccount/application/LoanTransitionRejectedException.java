package com.lms.loanaccount.application;

import com.lms.loanaccount.domain.LoanTransitionResult.Illegal;

/**
 * Thrown when a loan transition is rejected (an illegal pairing or a failed guard). The caller has
 * already recorded the rejection in {@code loan_state_history}; this surfaces it so the message is
 * retried or dead-lettered rather than silently dropped (docs/domain-rules.md state-machine
 * discipline). Mapped to an RFC 9457 problem detail at the REST boundary.
 */
public class LoanTransitionRejectedException extends RuntimeException {

    public LoanTransitionRejectedException(Illegal illegal) {
        super("loan transition rejected from " + illegal.from().code() + ": " + illegal.reason());
    }
}
