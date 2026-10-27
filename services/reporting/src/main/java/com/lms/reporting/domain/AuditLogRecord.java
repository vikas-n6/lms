package com.lms.reporting.domain;

import java.time.Instant;
import java.util.UUID;

/** A stored audit entry (ADR 0014), read back for the audit-log page. Append-only. */
public record AuditLogRecord(
        UUID eventId,
        String actorId,
        String actorRoles,
        String action,
        String targetType,
        String targetId,
        String beforeSummary,
        String afterSummary,
        String outcome,
        String correlationId,
        String traceId,
        Instant recordedAt) {}
