package com.lms.loanaccount.application;

import java.util.UUID;

/** Thrown when a loan referenced by an event or request does not exist. */
public class LoanNotFoundException extends RuntimeException {

    public LoanNotFoundException(UUID loanId) {
        super("loan not found: " + loanId);
    }
}
