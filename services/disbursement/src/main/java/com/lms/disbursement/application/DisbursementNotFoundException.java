package com.lms.disbursement.application;

import java.util.UUID;

/** Raised when a disbursement id/loan id has no persisted aggregate. */
public class DisbursementNotFoundException extends RuntimeException {
    public DisbursementNotFoundException(UUID id) {
        super("disbursement not found: " + id);
    }
}
