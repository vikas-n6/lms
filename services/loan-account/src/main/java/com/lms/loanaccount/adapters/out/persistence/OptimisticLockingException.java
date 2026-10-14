package com.lms.loanaccount.adapters.out.persistence;

/**
 * Raised when an optimistic-locked update finds the row already changed (CLAUDE.md "Persistence").
 */
public class OptimisticLockingException extends RuntimeException {

    public OptimisticLockingException(String message) {
        super(message);
    }
}
