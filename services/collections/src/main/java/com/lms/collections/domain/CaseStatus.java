package com.lms.collections.domain;

/**
 * The lifecycle of a collection case (phase prompt task 4). {@code RESOLVED}, {@code WRITTEN_OFF}
 * are terminal; {@code WRITE_OFF_RECOMMENDED} awaits the admin write-off decision.
 */
public enum CaseStatus {
    OPEN,
    PROMISE_TO_PAY,
    ESCALATED,
    RESOLVED,
    WRITE_OFF_RECOMMENDED,
    WRITTEN_OFF;

    public boolean isTerminal() {
        return this == RESOLVED || this == WRITTEN_OFF;
    }
}
