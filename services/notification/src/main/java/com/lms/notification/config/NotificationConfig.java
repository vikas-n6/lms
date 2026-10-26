package com.lms.notification.config;

import com.lms.events.AvroEventSerde;
import com.lms.notification.application.Backoff;
import com.lms.notification.application.NotificationService;
import com.lms.notification.application.port.out.ChannelGateway;
import com.lms.notification.application.port.out.ConsentPolicy;
import com.lms.notification.application.port.out.NotificationEventPublisher;
import com.lms.notification.application.port.out.NotificationLogRepository;
import com.lms.notification.application.port.out.TemplateCatalog;
import com.lms.notification.domain.QuietHours;
import com.lms.outbox.OutboxRepository;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.outbox.inbox.InboxStore;
import com.lms.outbox.jdbc.JdbcInboxStore;
import com.lms.outbox.jdbc.JdbcOutboxRepository;
import java.time.Clock;
import java.time.ZoneId;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires notification: outbox/inbox, Avro serde, quiet hours, backoff and the delivery service. */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Serializer<Object> avroSerializer(
            @Value("${lms.kafka.schema-registry-url}") String schemaRegistryUrl) {
        return AvroEventSerde.serializer(schemaRegistryUrl, true);
    }

    @Bean
    public OutboxRepository outboxRepository(JdbcClient jdbcClient) {
        return new JdbcOutboxRepository(jdbcClient);
    }

    @Bean
    public InboxStore inboxStore(JdbcClient jdbcClient) {
        return new JdbcInboxStore(jdbcClient);
    }

    @Bean
    public IdempotentConsumer idempotentConsumer(InboxStore inboxStore) {
        return new IdempotentConsumer(inboxStore);
    }

    @Bean
    public QuietHours quietHours(NotificationProperties properties) {
        return new QuietHours(
                properties.quietStartHour(),
                properties.quietEndHour(),
                ZoneId.of(properties.zone()));
    }

    @Bean
    public Backoff backoff(@Value("${lms.notification.backoff-base-millis:200}") long baseMillis) {
        // Exponential backoff between channel retries; never on the critical request path.
        return attempt -> {
            try {
                Thread.sleep(baseMillis * (1L << Math.min(attempt - 1, 5)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    @Bean
    public NotificationService notificationService(
            TemplateCatalog templates,
            NotificationLogRepository log,
            ChannelGateway gateway,
            ConsentPolicy consent,
            NotificationEventPublisher publisher,
            QuietHours quietHours,
            Backoff backoff,
            Clock clock,
            NotificationProperties properties) {
        return new NotificationService(
                templates,
                log,
                gateway,
                consent,
                publisher,
                quietHours,
                backoff,
                clock,
                properties.maxAttempts());
    }
}
