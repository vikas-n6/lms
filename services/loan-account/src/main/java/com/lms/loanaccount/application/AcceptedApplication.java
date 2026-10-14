package com.lms.loanaccount.application;

import com.lms.money.Money;
import java.util.Objects;

/**
 * The accepted offer terms carried by {@code application.accepted} (mapped from the Avro event by
 * the inbound adapter). loan-account creates a loan from these, pinning the current product version
 * and drafting a provisional schedule. The nominal APR for the schedule comes from the product, not
 * this command (the event carries only the displayed effective rate).
 */
public record AcceptedApplication(
        String applicationId,
        String customerId,
        String productId,
        Money principal,
        int tenorMonths,
        Money emi,
        Money fee,
        Money gst,
        Money netDisbursal) {

    public AcceptedApplication {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(emi, "emi");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(gst, "gst");
        Objects.requireNonNull(netDisbursal, "netDisbursal");
        if (tenorMonths <= 0) {
            throw new IllegalArgumentException("tenorMonths must be positive");
        }
    }
}
