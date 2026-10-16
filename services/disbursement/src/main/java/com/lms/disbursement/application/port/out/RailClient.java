package com.lms.disbursement.application.port.out;

import java.util.UUID;

/**
 * Outbound port to the payment rail (implemented by an HTTP adapter against the rail simulator).
 * Resilience4j wraps the real calls. The rail is idempotent on {@code idempotencyKey}, so a retried
 * {@link #initiatePayout} never issues a second payout.
 */
public interface RailClient {

    /** Issue (or idempotently replay) a payout. */
    Ack initiatePayout(PayoutRequest request);

    /** Query a payout's status by its idempotency key (after a lost response or a timeout). */
    StatusResult queryStatus(String idempotencyKey);

    record PayoutRequest(
            String idempotencyKey,
            UUID loanId,
            long amountMinor,
            String currency,
            String callbackUrl) {}

    /** Synchronous acknowledgement: accepted (await callback) or the response was lost. */
    record Ack(boolean responseLost, String railRef) {}

    enum Outcome {
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    record StatusResult(Outcome outcome, String railRef, String utr, String failureCode) {}
}
