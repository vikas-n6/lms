package com.lms.ledger.application;

import java.util.UUID;

/** Thrown when an entry that has already been reversed is reversed again (rejected). */
public class EntryAlreadyReversedException extends RuntimeException {

    public EntryAlreadyReversedException(UUID entryId) {
        super("journal entry already reversed: " + entryId);
    }
}
