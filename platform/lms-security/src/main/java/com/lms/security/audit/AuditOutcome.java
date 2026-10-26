package com.lms.security.audit;

/** The result of an audited admin action. */
public enum AuditOutcome {
    /** The action completed and its effect is persisted. */
    SUCCESS,
    /** The action was rejected by a guard (authorization, validation, business rule). */
    DENIED,
    /** The action failed with an unexpected error after passing its guards. */
    ERROR
}
