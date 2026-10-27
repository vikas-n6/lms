package com.lms.reporting.application;

import com.lms.contracts.audit.AuditEvent;
import com.lms.reporting.application.port.out.AuditLogRepository;
import com.lms.reporting.domain.AuditLogRecord;
import java.util.Objects;
import java.util.UUID;

/** Stores audit.events into the append-only audit log (ADR 0014), idempotent on the event id. */
public class AuditProjection {

    private final AuditLogRepository auditLog;

    public AuditProjection(AuditLogRepository auditLog) {
        this.auditLog = Objects.requireNonNull(auditLog);
    }

    public void onAuditEvent(AuditEvent e) {
        auditLog.insert(
                new AuditLogRecord(
                        UUID.fromString(e.getMetadata().getEventId()),
                        e.getActorId(),
                        e.getActorRoles(),
                        e.getAction(),
                        e.getTargetType(),
                        e.getTargetId(),
                        e.getBeforeSummary(),
                        e.getAfterSummary(),
                        e.getOutcome(),
                        e.getMetadata().getCorrelationId(),
                        e.getMetadata().getTraceId(),
                        e.getRecordedAt()));
    }
}
