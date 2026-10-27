package com.lms.reporting.application.port.out;

import com.lms.reporting.domain.AuditLogRecord;
import com.lms.reporting.domain.AuditQuery;
import java.util.List;

/** Append-only audit store (ADR 0014). {@code insert} is idempotent on the event id. */
public interface AuditLogRepository {

    void insert(AuditLogRecord record);

    List<AuditLogRecord> query(AuditQuery query);
}
