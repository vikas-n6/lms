package com.lms.simulators.payments;

/** A payment link recorded by the payments simulator. */
public record PaymentLink(
        String linkId,
        String url,
        String loanId,
        long amountMinor,
        String currency,
        String status) {

    public static final String CREATED = "CREATED";
    public static final String COMPLETED = "COMPLETED";
}
