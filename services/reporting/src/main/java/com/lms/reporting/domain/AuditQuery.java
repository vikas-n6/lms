package com.lms.reporting.domain;

import java.time.Instant;

/**
 * Filters for the audit-log query; any field may be null to mean "no filter". {@code limit} is
 * clamped to a sane range by the query service so a page request cannot scan the whole table.
 */
public record AuditQuery(
        String actorId,
        String action,
        String targetType,
        String targetId,
        Instant from,
        Instant to,
        int limit) {}
