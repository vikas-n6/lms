package com.lms.ledger.application;

import java.util.UUID;

/** Thrown when a journal entry referenced by a reversal or query does not exist. */
public class EntryNotFoundException extends RuntimeException {

    public EntryNotFoundException(UUID entryId) {
        super("journal entry not found: " + entryId);
    }
}
