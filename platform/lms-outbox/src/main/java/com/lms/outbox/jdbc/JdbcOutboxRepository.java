package com.lms.outbox.jdbc;

import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * JDBC {@link OutboxRepository} over the {@code outbox} table. {@link #lockUnpublishedBatch} uses
 * {@code FOR UPDATE SKIP LOCKED} so concurrent relays take disjoint rows. Headers are stored as a
 * jsonb map of base64-encoded values.
 */
public final class JdbcOutboxRepository implements OutboxRepository {

    private final JdbcClient jdbc;

    public JdbcOutboxRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void append(OutboxRecord record) {
        jdbc.sql(
                        """
                        insert into outbox (id, aggregate_type, aggregate_id, event_type, event_id,
                                            topic, msg_key, payload, headers, attempts, created_at)
                        values (:id, :aggregateType, :aggregateId, :eventType, :eventId,
                                :topic, :key, :payload, cast(:headers as jsonb), :attempts, :createdAt)
                        """)
                .param("id", record.id())
                .param("aggregateType", record.aggregateType())
                .param("aggregateId", record.aggregateId())
                .param("eventType", record.eventType())
                .param("eventId", record.eventId())
                .param("topic", record.topic())
                .param("key", record.key())
                .param("payload", record.payload())
                .param("headers", toJson(record.headers()))
                .param("attempts", record.attempts())
                .param("createdAt", java.sql.Timestamp.from(record.createdAt()))
                .update();
    }

    @Override
    public List<OutboxRecord> lockUnpublishedBatch(int limit) {
        return jdbc.sql(
                        """
                        select id, aggregate_type, aggregate_id, event_type, event_id, topic,
                               msg_key, payload, headers, attempts, last_error, created_at, published_at
                        from outbox
                        where published_at is null
                        order by created_at
                        limit :limit
                        for update skip locked
                        """)
                .param("limit", limit)
                .query(JdbcOutboxRepository::mapRow)
                .list();
    }

    @Override
    public void markPublished(UUID id) {
        jdbc.sql("update outbox set published_at = now() where id = :id").param("id", id).update();
    }

    @Override
    public void recordFailure(UUID id, String error) {
        jdbc.sql("update outbox set attempts = attempts + 1, last_error = :error where id = :id")
                .param("id", id)
                .param("error", error)
                .update();
    }

    private static OutboxRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Timestamp published = rs.getTimestamp("published_at");
        return new OutboxRecord(
                rs.getObject("id", UUID.class),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getObject("event_id", UUID.class),
                rs.getString("topic"),
                rs.getBytes("msg_key"),
                rs.getBytes("payload"),
                fromJson(rs.getString("headers")),
                rs.getInt("attempts"),
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant(),
                published == null ? null : published.toInstant());
    }

    private static String toJson(Map<String, byte[]> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        Base64.Encoder encoder = Base64.getEncoder();
        return headers.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":\"" + encoder.encodeToString(e.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static Map<String, byte[]> fromJson(String json) {
        Map<String, byte[]> headers = new LinkedHashMap<>();
        if (json == null) {
            return headers;
        }
        String trimmed = json.trim();
        if (trimmed.length() <= 2) {
            return headers;
        }
        Base64.Decoder decoder = Base64.getDecoder();
        for (String pair : trimmed.substring(1, trimmed.length() - 1).split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                headers.put(key, decoder.decode(value));
            }
        }
        return headers;
    }
}
