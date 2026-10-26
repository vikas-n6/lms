package com.lms.loanaccount.application;

/**
 * Thrown when a write-off cannot proceed: DPD below the configured minimum, the request is no
 * longer pending, or the loan is not in a state that can be written off (phase prompt task 2).
 */
public class WriteOffNotAllowedException extends RuntimeException {

    public WriteOffNotAllowedException(String message) {
        super(message);
    }
}
