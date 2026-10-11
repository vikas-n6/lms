package com.lms.customer.config;

import com.lms.events.AvroEventSerde;
import com.lms.outbox.OutboxRepository;
import com.lms.outbox.jdbc.JdbcOutboxRepository;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires the platform adapters the customer service uses (outbox + Avro serde). */
@Configuration
public class CustomerConfig {

    @Bean
    public OutboxRepository outboxRepository(JdbcClient jdbcClient) {
        return new JdbcOutboxRepository(jdbcClient);
    }

    @Bean
    public Serializer<Object> avroSerializer(
            @Value("${lms.kafka.schema-registry-url}") String schemaRegistryUrl) {
        return AvroEventSerde.serializer(schemaRegistryUrl, true);
    }
}
