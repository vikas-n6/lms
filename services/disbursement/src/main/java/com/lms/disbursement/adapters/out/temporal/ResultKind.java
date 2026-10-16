package com.lms.disbursement.adapters.out.temporal;

/**
 * Kind of rail result the workflow observes. {@code PENDING} = the payout was accepted and a
 * callback is awaited; {@code UNKNOWN} = the result could not be established (lost response or
 * exhausted polling) — the workflow never re-pays on this, it queries status; {@code
 * SUCCESS}/{@code FAILED} are terminal.
 */
public enum ResultKind {
    PENDING,
    SUCCESS,
    FAILED,
    UNKNOWN;

    public static boolean isTerminal(ResultKind kind) {
        return kind == SUCCESS || kind == FAILED;
    }
}
