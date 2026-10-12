package com.lms.decision.config;

import com.lms.decision.application.BureauBankFanout;
import com.lms.decision.application.port.out.BankClient;
import com.lms.decision.application.port.out.BureauClient;
import com.lms.decision.application.port.out.MarketDataProvider;
import com.lms.events.AvroEventSerde;
import com.lms.outbox.OutboxRepository;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.outbox.inbox.InboxStore;
import com.lms.outbox.jdbc.JdbcInboxStore;
import com.lms.outbox.jdbc.JdbcOutboxRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires the decision service's platform adapters (outbox, inbox, Avro serde, Resilience4j). */
@Configuration
public class DecisionConfig {

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
    public MarketDataProvider marketDataProvider(BureauClient bureauClient, BankClient bankClient) {
        return new BureauBankFanout(bureauClient, bankClient);
    }

    private static Retry retry(String name) {
        return Retry.of(
                name,
                RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(200)).build());
    }

    @Bean
    public Retry bureauRetry() {
        return retry("bureau");
    }

    @Bean
    public CircuitBreaker bureauCircuitBreaker() {
        return CircuitBreaker.ofDefaults("bureau");
    }

    @Bean
    public Retry bankRetry() {
        return retry("bank");
    }

    @Bean
    public CircuitBreaker bankCircuitBreaker() {
        return CircuitBreaker.ofDefaults("bank");
    }
}
