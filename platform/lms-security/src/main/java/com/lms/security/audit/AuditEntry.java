package com.lms.security.audit;

import java.util.Objects;

/**
 * An admin action to be audited (ADR 0014): who ({@code actorId}, {@code actorRoles}), what ({@code
 * action}), on which target ({@code targetType}/{@code targetId}), with a PII-free before/after
 * summary and the {@link AuditOutcome}. This is the pure, Spring-free domain value that {@code
 * lms-security} owns; the envelope (eventId, correlationId, traceId, clock instant) and the
 * Avro/outbox mapping are added by the publishing service's adapter, so the platform stays free of
 * contracts/Kafka dependencies.
 *
 * <p>Summaries must be built with {@link AuditSummary} so they never carry raw PII (CLAUDE.md
 * "Logging"); they may be null when there is no prior/after state (a create has no before; a deny
 * has no after).
 */
public record AuditEntry(
        String actorId,
        String actorRoles,
        String action,
        String targetType,
        String targetId,
        String beforeSummary,
        String afterSummary,
        AuditOutcome outcome) {

    /** Placeholder actor id for a system or unauthenticated action; the trail is never dropped. */
    public static final String UNKNOWN_ACTOR = "unknown";

    public AuditEntry {
        actorId = requireText(actorId, "actorId");
        action = requireText(action, "action");
        targetType = requireText(targetType, "targetType");
        targetId = requireText(targetId, "targetId");
        actorRoles = actorRoles == null ? "" : actorRoles;
        Objects.requireNonNull(outcome, "outcome");
    }

    /** Canonical factory (keeps call sites readable and the record constructor validating). */
    public static AuditEntry of(
            String actorId,
            String actorRoles,
            String action,
            String targetType,
            String targetId,
            String beforeSummary,
            String afterSummary,
            AuditOutcome outcome) {
        return new AuditEntry(
                actorId,
                actorRoles,
                action,
                targetType,
                targetId,
                beforeSummary,
                afterSummary,
                outcome);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
