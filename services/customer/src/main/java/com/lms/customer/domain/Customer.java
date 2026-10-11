package com.lms.customer.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The customer aggregate. Pure domain: no Spring, no persistence, no PII in its {@link
 * #displayName} projection. Phase 0 keeps this a thin skeleton (no eligibility/KYC logic — that
 * lives in other services).
 */
public record Customer(
        CustomerId id,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        CustomerStatus status,
        Instant createdAt) {

    public Customer {
        Objects.requireNonNull(id, "id");
        requireText(fullName, "fullName");
        requireText(phone, "phone");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
    }

    /** Register a new customer in the REGISTERED state, with ids and timestamp from the clock. */
    public static Customer register(
            Clock clock, String fullName, String phone, LocalDate dateOfBirth) {
        return new Customer(
                CustomerId.newId(clock),
                fullName,
                phone,
                dateOfBirth,
                CustomerStatus.REGISTERED,
                clock.instant());
    }

    /** A non-PII label safe to put in events and the UI (the given name only). */
    public String displayName() {
        String trimmed = fullName.strip();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
