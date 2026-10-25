package com.lms.repayment.application.port.out;

import com.lms.money.Money;
import java.util.UUID;

/** Outbound port to the mandate simulator (implemented by an HTTP adapter). */
public interface MandateClient {

    /** Register a mandate; returns the provider's mandate id. */
    String register(UUID loanId, String customerId, String callbackUrl);

    /** Request an auto-debit (idempotent on the key); the terminal result arrives via callback. */
    void debit(
            String providerMandateId,
            UUID loanId,
            Money amount,
            String idempotencyKey,
            String callbackUrl);

    void cancel(String providerMandateId);
}
