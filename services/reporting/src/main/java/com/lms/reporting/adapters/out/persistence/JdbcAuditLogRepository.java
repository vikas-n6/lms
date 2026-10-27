package com.lms.reporting.adapters.out.persistence;

import com.lms.reporting.application.port.out.AuditLogRepository;
import com.lms.reporting.domain.AuditLogRecord;
import com.lms.reporting.domain.AuditQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Append-only audit store (ADR 0014). {@code insert} is idempotent on the event id (primary key);
 * {@code query} builds a filtered, bounded, newest-first page. Rows are never updated or deleted.
 */
@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {

    private final JdbcClient jdbc;

    public JdbcAuditLogRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void insert(AuditLogRecord r) {
        jdbc.sql(
                        """
                        insert into audit_log (event_id, actor_id, actor_roles, action, target_type,
                            target_id, before_summary, after_summary, outcome, correlation_id,
                            trace_id, recorded_at, stored_at)
                        values (:eventId, :actorId, :actorRoles, :action, :targetType, :targetId,
                            :before, :after, :outcome, :correlationId, :traceId, :recordedAt, now())
                        on conflict (event_id) do nothing
                        """)
                .param("eventId", r.eventId())
                .param("actorId", r.actorId())
                .param("actorRoles", r.actorRoles())
                .param("action", r.action())
                .param("targetType", r.targetType())
                .param("targetId", r.targetId())
                .param("before", r.beforeSummary())
                .param("after", r.afterSummary())
                .param("outcome", r.outcome())
                .param("correlationId", r.correlationId())
                .param("traceId", r.traceId())
                .param("recordedAt", java.sql.Timestamp.from(r.recordedAt()))
                .update();
    }

    @Override
    public List<AuditLogRecord> query(AuditQuery q) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        select event_id, actor_id, actor_roles, action, target_type, target_id,
                               before_summary, after_summary, outcome, correlation_id, trace_id,
                               recorded_at
                        from audit_log
                        where 1 = 1
                        """);
        List<Object[]> params = new ArrayList<>();
        if (q.actorId() != null) {
            sql.append(" and actor_id = :actorId");
            params.add(new Object[] {"actorId", q.actorId()});
        }
        if (q.action() != null) {
            sql.append(" and action = :action");
            params.add(new Object[] {"action", q.action()});
        }
        if (q.targetType() != null) {
            sql.append(" and target_type = :targetType");
            params.add(new Object[] {"targetType", q.targetType()});
        }
        if (q.targetId() != null) {
            sql.append(" and target_id = :targetId");
            params.add(new Object[] {"targetId", q.targetId()});
        }
        if (q.from() != null) {
            sql.append(" and recorded_at >= :from");
            params.add(new Object[] {"from", java.sql.Timestamp.from(q.from())});
        }
        if (q.to() != null) {
            sql.append(" and recorded_at <= :to");
            params.add(new Object[] {"to", java.sql.Timestamp.from(q.to())});
        }
        sql.append(" order by recorded_at desc limit :limit");

        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        for (Object[] p : params) {
            spec = spec.param((String) p[0], p[1]);
        }
        return spec.param("limit", q.limit()).query(JdbcAuditLogRepository::mapRow).list();
    }

    private static AuditLogRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLogRecord(
                rs.getObject("event_id", java.util.UUID.class),
                rs.getString("actor_id"),
                rs.getString("actor_roles"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("before_summary"),
                rs.getString("after_summary"),
                rs.getString("outcome"),
                rs.getString("correlation_id"),
                rs.getString("trace_id"),
                rs.getTimestamp("recorded_at").toInstant());
    }
}
