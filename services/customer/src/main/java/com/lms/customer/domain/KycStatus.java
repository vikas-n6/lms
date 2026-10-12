package com.lms.customer.domain;

/**
 * KYC status for a customer (phase prompt S3). The KYC simulator may resolve synchronously
 * (VERIFIED/FAILED) or return PENDING and resolve later via an asynchronous callback.
 *
 * <p>Allowed transitions: NOT_STARTED → PENDING | VERIFIED | FAILED; PENDING → VERIFIED | FAILED.
 * VERIFIED and FAILED are terminal. {@link #canTransitionTo} is a pure guard; illegal transitions
 * are rejected, never applied silently.
 */
public enum KycStatus {
    NOT_STARTED,
    PENDING,
    VERIFIED,
    FAILED;

    public boolean isTerminal() {
        return this == VERIFIED || this == FAILED;
    }

    public boolean canTransitionTo(KycStatus target) {
        return switch (this) {
            case NOT_STARTED -> target == PENDING || target == VERIFIED || target == FAILED;
            case PENDING -> target == VERIFIED || target == FAILED;
            case VERIFIED, FAILED -> false;
        };
    }

    /** Apply a transition, or throw if it is not allowed. */
    public KycStatus transitionTo(KycStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("illegal KYC transition " + this + " -> " + target);
        }
        return target;
    }
}
