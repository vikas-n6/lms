package com.lms.collections.adapters.out.persistence;

/** Raised when an optimistic-locked update finds the row changed concurrently. */
public class OptimisticLockingException extends RuntimeException {

    public OptimisticLockingException(String message) {
        super(message);
    }
}
