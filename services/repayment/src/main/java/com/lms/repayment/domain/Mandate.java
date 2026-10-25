package com.lms.repayment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An auto-debit mandate for a loan. Immutable; {@code cancel} returns a new instance. The repayment
 * service owns the mandate lifecycle and never touches the loan database (phase prompt ownership).
 */
public record Mandate(
        UUID id,
        UUID loanId,
        String customerId,
        String providerMandateId,
        MandateStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public Mandate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(loanId, "loanId");
        Objects.requireNonNull(status, "status");
    }

    public static Mandate register(
            UUID id, UUID loanId, String customerId, String providerMandateId, Instant now) {
        return new Mandate(
                id, loanId, customerId, providerMandateId, MandateStatus.ACTIVE, now, now);
    }

    public Mandate cancel(Instant now) {
        return new Mandate(
                id, loanId, customerId, providerMandateId, MandateStatus.CANCELLED, createdAt, now);
    }
}
