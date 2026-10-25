package com.lms.repayment.domain;

/** How a repayment was collected (carried on {@code repayment.received}). */
public enum RepaymentChannel {
    MANDATE,
    PAYMENT_LINK,
    MANUAL
}
