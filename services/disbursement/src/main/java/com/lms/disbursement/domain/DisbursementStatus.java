package com.lms.disbursement.domain;

/**
 * Lifecycle of a payout saga (phase prompt S2). {@code INITIATED} once the payout is issued and we
 * await the rail result; {@code SUCCEEDED}/{@code FAILED} are terminal; {@code MANUAL_REVIEW} is
 * the terminal-for-automation state the workflow parks in when a result cannot be established
 * within the bounded polling rounds, to be retried or cancelled by an operator.
 */
public enum DisbursementStatus {
    INITIATED,
    SUCCEEDED,
    FAILED,
    MANUAL_REVIEW;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
