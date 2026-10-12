package com.lms.decision.application;

/** Raised when a decision id is not found (mapped to 404 by the web adapter). */
public class DecisionNotFoundException extends RuntimeException {
    public DecisionNotFoundException(String id) {
        super("decision not found: " + id);
    }
}
