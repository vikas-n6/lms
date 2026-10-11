package com.lms.outbox.jdbc;

import com.lms.outbox.inbox.InboxStore;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * JDBC {@link InboxStore} backed by the {@code inbox_processed} table. The primary-key insert is
 * the dedup guard: a duplicate event id raises {@link DuplicateKeyException}, which we translate to
 * a {@code false} ("already processed") result. Runs within the consumer's transaction.
 */
public final class JdbcInboxStore implements InboxStore {

    private final JdbcClient jdbc;

    public JdbcInboxStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public boolean markProcessed(UUID eventId, String topic) {
        try {
            int inserted =
                    jdbc.sql("insert into inbox_processed (event_id, topic) values (:id, :topic)")
                            .param("id", eventId)
                            .param("topic", topic)
                            .update();
            return inserted == 1;
        } catch (DuplicateKeyException alreadyProcessed) {
            return false;
        }
    }
}
