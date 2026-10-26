package com.lms.loanaccount.application;

import java.util.UUID;

/** Thrown when a write-off request referenced by a request does not exist. */
public class WriteOffRequestNotFoundException extends RuntimeException {

    public WriteOffRequestNotFoundException(UUID requestId) {
        super("write-off request not found: " + requestId);
    }
}
