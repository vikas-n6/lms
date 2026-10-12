package com.lms.customer.domain;

import com.lms.events.Uuid7;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A linked payout bank account (phase prompt S3). The domain holds only the masked number; the real
 * number is encrypted at the persistence layer and never logged or published.
 */
public record BankAccount(
        UUID id,
        CustomerId customerId,
        String maskedNumber,
        String ifsc,
        boolean pennyDropVerified,
        Instant linkedAt) {

    public BankAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(maskedNumber, "maskedNumber");
        Objects.requireNonNull(ifsc, "ifsc");
        Objects.requireNonNull(linkedAt, "linkedAt");
    }

    public static BankAccount linked(
            Clock clock,
            CustomerId customerId,
            String maskedNumber,
            String ifsc,
            boolean pennyDropVerified) {
        return new BankAccount(
                Uuid7.generate(clock),
                customerId,
                maskedNumber,
                ifsc,
                pennyDropVerified,
                clock.instant());
    }
}
