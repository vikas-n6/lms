package com.lms.disbursement.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded attempt to pay out (phase prompt S2: "each attempt recorded in payout_attempts").
 * The {@code idempotencyKey} is tied to the disbursement and attempt, so the rail dedups a retried
 * POST and we never issue two payouts for the same key. {@code UNKNOWN} records an ambiguous result
 * that was resolved by a status query rather than a blind re-payout.
 */
public record PayoutAttempt(
        UUID id,
        UUID disbursementId,
        int attemptNumber,
        String idempotencyKey,
        Status status,
        String failureCode,
        Instant startedAt,
        Instant finishedAt) {

    public enum Status {
        PENDING,
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    public static String idempotencyKey(UUID disbursementId, int attemptNumber) {
        return "disb-" + disbursementId + "-attempt-" + attemptNumber;
    }

    public PayoutAttempt finished(Status finalStatus, String failureCode, Instant now) {
        return new PayoutAttempt(
                id,
                disbursementId,
                attemptNumber,
                idempotencyKey,
                finalStatus,
                failureCode,
                startedAt,
                now);
    }
}
