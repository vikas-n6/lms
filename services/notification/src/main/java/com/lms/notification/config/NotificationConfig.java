package com.lms.notification.config;

import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.outbox.inbox.InboxStore;
import com.lms.outbox.jdbc.JdbcInboxStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Inbox wiring for the notification consumer. */
@Configuration
public class NotificationConfig {

    @Bean
    public InboxStore inboxStore(JdbcClient jdbcClient) {
        return new JdbcInboxStore(jdbcClient);
    }

    @Bean
    public IdempotentConsumer idempotentConsumer(InboxStore inboxStore) {
        return new IdempotentConsumer(inboxStore);
    }
}
