package com.lms.decision.domain;

/**
 * Stable reason codes attached to a decision (docs/domain-rules.md "Eligibility rules"). Persisted
 * and published so decisions can be explained and replayed.
 */
public enum ReasonCode {
    AGE_OUT_OF_RANGE,
    KYC_NOT_VERIFIED,
    BUREAU_SCORE_LOW,
    BUREAU_DELINQUENT,
    INCOME_TOO_LOW,
    FOIR_EXCEEDED,
    ACTIVE_LOAN_EXISTS,
    VELOCITY_BREACH,
    /** The bureau was unavailable after retries; the decision refers rather than hanging. */
    BUREAU_UNAVAILABLE
}
