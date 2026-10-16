package com.lms.simulators.rail;

/**
 * A payout recorded by the rail simulator, one per {@code idempotencyKey}. The {@code outcome}
 * (SUCCESS/FAILED) is decided once at creation and is what the asynchronous callback and the status
 * query both report, so a lost response never produces a second, divergent payout (phase prompt S3
 * unknown-outcome rule: "the rail simulator records exactly one payout for the key").
 */
public record Payout(
        String idempotencyKey,
        String railRef,
        String loanId,
        long amountMinor,
        String currency,
        String outcome,
        String failureCode) {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    public boolean succeeded() {
        return SUCCESS.equals(outcome);
    }
}
