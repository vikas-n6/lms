package com.lms.repayment.domain;

/** Lifecycle of a repayment the service records. */
public enum RepaymentStatus {
    PENDING,
    RECEIVED,
    REVERSED,
    FAILED
}
