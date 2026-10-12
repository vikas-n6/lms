package com.lms.customer.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A single consent grant (phase prompt S3). Consents are append-only records (never updated or
 * deleted), so the full history of what was granted and when is preserved for audit.
 *
 * @param type what was consented to
 * @param grantedAt UTC instant the consent was granted (from the injected Clock)
 */
public record Consent(ConsentType type, Instant grantedAt) {

    public Consent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(grantedAt, "grantedAt");
    }
}
