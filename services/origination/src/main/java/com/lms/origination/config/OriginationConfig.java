package com.lms.origination.config;

import com.lms.events.AvroEventSerde;
import com.lms.origination.application.OfferPricing;
import com.lms.outbox.OutboxRepository;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.outbox.inbox.InboxStore;
import com.lms.outbox.jdbc.JdbcInboxStore;
import com.lms.outbox.jdbc.JdbcOutboxRepository;
import com.lms.schedule.ReducingBalanceScheduleEngine;
import com.lms.security.crypto.BlindIndex;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the origination service's platform adapters (outbox, inbox, serde, pricing, blind index).
 */
@Configuration
@EnableScheduling
public class OriginationConfig {

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
    public OfferPricing offerPricing() {
        return new OfferPricing(new ReducingBalanceScheduleEngine());
    }

    @Bean
    public BlindIndex phoneBlindIndex(
            @Value("${lms.security.blind-index-key:origination-dev-blind-index-key!!}")
                    String key) {
        return new BlindIndex(key.getBytes(StandardCharsets.UTF_8));
    }
}
