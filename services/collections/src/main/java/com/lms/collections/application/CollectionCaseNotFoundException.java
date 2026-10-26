package com.lms.collections.application;

import java.util.UUID;

/** Thrown when a collection case referenced by an admin request does not exist. */
public class CollectionCaseNotFoundException extends RuntimeException {

    public CollectionCaseNotFoundException(UUID caseId) {
        super("collection case not found: " + caseId);
    }
}
