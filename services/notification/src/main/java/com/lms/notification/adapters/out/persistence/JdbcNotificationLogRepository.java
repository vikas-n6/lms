package com.lms.notification.adapters.out.persistence;

import com.lms.notification.application.port.out.NotificationLogRepository;
import com.lms.notification.domain.Channel;
import com.lms.notification.domain.NotificationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC persistence for the notification log (no JPA). Dedup is enforced by the unique {@code
 * (trigger_event_id, template_id, channel)}: a duplicate insert is caught and reported as "already
 * exists" so a replay sends no second message (phase prompt task 5).
 */
@Repository
public class JdbcNotificationLogRepository implements NotificationLogRepository {

    private final JdbcClient jdbc;

    public JdbcNotificationLogRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<UUID> createIfAbsent(
            String triggerEventId,
            String templateId,
            Channel channel,
            String recipientRef,
            String body,
            Instant releaseAt,
            Instant now) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql(
                            """
                            insert into notification_log (id, trigger_event_id, template_id, channel,
                                status, recipient_ref, body, attempts, release_at, created_at,
                                updated_at, version)
                            values (:id, :trigger, :template, :channel, 'QUEUED', :recipient, :body,
                                0, :releaseAt, :now, :now, 0)
                            """)
                    .param("id", id)
                    .param("trigger", triggerEventId)
                    .param("template", templateId)
                    .param("channel", channel.name())
                    .param("recipient", recipientRef)
                    .param("body", body)
                    .param(
                            "releaseAt",
                            releaseAt == null ? null : java.sql.Timestamp.from(releaseAt))
                    .param("now", java.sql.Timestamp.from(now))
                    .update();
            return Optional.of(id);
        } catch (DuplicateKeyException duplicate) {
            return Optional.empty(); // already queued for this (trigger, template, channel)
        }
    }

    @Override
    public void updateStatus(UUID id, NotificationStatus status, int attempts, String failureCode) {
        jdbc.sql(
                        """
                        update notification_log set status = :status, attempts = :attempts,
                            failure_code = :code,
                            release_at = case when :status = 'QUEUED' then release_at else null end,
                            updated_at = now(), version = version + 1
                        where id = :id
                        """)
                .param("status", status.name())
                .param("attempts", attempts)
                .param("code", failureCode)
                .param("id", id)
                .update();
    }

    @Override
    public List<Held> findDue(Instant now) {
        return jdbc.sql(
                        """
                        select id, trigger_event_id, template_id, channel, recipient_ref, body
                          from notification_log
                         where status = 'QUEUED' and release_at is not null and release_at <= :now
                        """)
                .param("now", java.sql.Timestamp.from(now))
                .query(
                        (rs, n) ->
                                new Held(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("trigger_event_id"),
                                        rs.getString("template_id"),
                                        Channel.valueOf(rs.getString("channel")),
                                        rs.getString("recipient_ref"),
                                        rs.getString("body")))
                .list();
    }

    @Override
    public List<Row> list(Optional<String> status) {
        return jdbc.sql(
                        """
                        select id, trigger_event_id, template_id, channel, status, attempts,
                               failure_code, created_at
                          from notification_log
                         where (:status is null or status = :status)
                         order by created_at desc
                        """)
                .param("status", status.orElse(null))
                .query(JdbcNotificationLogRepository::mapRow)
                .list();
    }

    private static Row mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getString("trigger_event_id"),
                rs.getString("template_id"),
                Channel.valueOf(rs.getString("channel")),
                NotificationStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getString("failure_code"),
                rs.getTimestamp("created_at").toInstant());
    }
}
