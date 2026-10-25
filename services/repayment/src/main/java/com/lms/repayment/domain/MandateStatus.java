package com.lms.repayment.domain;

/** Lifecycle of an auto-debit mandate (phase prompt task 3). */
public enum MandateStatus {
    PENDING,
    ACTIVE,
    CANCELLED,
    FAILED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
