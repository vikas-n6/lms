package com.lms.security.audit;

/**
 * Port for recording an admin action to the audit trail (ADR 0014). {@code lms-security} owns the
 * port and the {@link AuditEntry} domain; each service provides an adapter that maps the entry onto
 * the {@code audit.events} Avro envelope and appends it to that service's outbox — so publishing
 * still goes only through the outbox (CLAUDE.md global do-not list) and the platform keeps no Kafka
 * dependency. The call joins the same transaction as the action it audits, so an audited change and
 * its audit row commit together or not at all.
 */
public interface AuditRecorder {

    void record(AuditEntry entry);
}
