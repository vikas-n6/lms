package com.lms.repayment.domain;

import com.lms.money.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A repayment recorded by the service. Immutable; status transitions return new instances. The
 * amount is {@link Money} (minor units + currency), never a double (CLAUDE.md). {@code FORECLOSURE}
 * carries the quote id so loan-account can re-validate against the quote.
 */
public record Repayment(
        UUID id,
        UUID loanId,
        Money amount,
        RepaymentChannel channel,
        String purpose,
        String foreclosureQuoteId,
        String providerReference,
        RepaymentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static final String FORECLOSURE = "FORECLOSURE";

    public Repayment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(loanId, "loanId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(status, "status");
    }

    public static Repayment received(
            UUID id,
            UUID loanId,
            Money amount,
            RepaymentChannel channel,
            String purpose,
            String foreclosureQuoteId,
            String providerReference,
            Instant now) {
        return new Repayment(
                id,
                loanId,
                amount,
                channel,
                purpose,
                foreclosureQuoteId,
                providerReference,
                RepaymentStatus.RECEIVED,
                now,
                now);
    }

    public Repayment reversed(Instant now) {
        return new Repayment(
                id,
                loanId,
                amount,
                channel,
                purpose,
                foreclosureQuoteId,
                providerReference,
                RepaymentStatus.REVERSED,
                createdAt,
                now);
    }

    public boolean isForeclosure() {
        return FORECLOSURE.equals(purpose);
    }
}
