package com.lms.disbursement.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Bounded exponential backoff for payout retries (phase prompt S2: "up to 3 attempts with
 * exponential backoff"). Pure and configuration-driven — the values come from service
 * configuration, never hard-coded at the call site (CLAUDE.md "no hard-coded business parameters").
 *
 * <p>Attempt numbers are 1-based: attempt 1 is the first try. A retry is allowed only after a
 * <em>definite</em> failure; an ambiguous (unknown) outcome is never retried blindly (that rule
 * lives in the workflow, which queries status instead).
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, double multiplier) {

    public RetryPolicy {
        Objects.requireNonNull(initialBackoff, "initialBackoff");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("initialBackoff must be positive");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
    }

    /** Whether another attempt is allowed after {@code attemptsMade} definite failures. */
    public boolean shouldRetry(int attemptsMade) {
        return attemptsMade < maxAttempts;
    }

    /** Backoff before the given 1-based attempt number (attempt 1 has no preceding wait). */
    public Duration backoffBefore(int attemptNumber) {
        if (attemptNumber <= 1) {
            return Duration.ZERO;
        }
        double factor = Math.pow(multiplier, attemptNumber - 2.0);
        long millis = Math.round(initialBackoff.toMillis() * factor);
        return Duration.ofMillis(millis);
    }
}
