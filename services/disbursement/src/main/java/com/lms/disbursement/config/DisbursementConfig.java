package com.lms.disbursement.config;

import com.lms.disbursement.adapters.out.temporal.SagaTimeouts;
import com.lms.disbursement.application.CallbackClassifier;
import com.lms.disbursement.application.DisbursementQueryService;
import com.lms.disbursement.application.DisbursementService;
import com.lms.disbursement.application.RegisterDisbursementService;
import com.lms.disbursement.application.port.out.CompliancePolicy;
import com.lms.disbursement.application.port.out.DisbursementEventPublisher;
import com.lms.disbursement.application.port.out.DisbursementRepository;
import com.lms.disbursement.application.port.out.PayoutAttemptRepository;
import com.lms.disbursement.application.port.out.RailCallbackDedupStore;
import com.lms.disbursement.application.port.out.RailClient;
import com.lms.disbursement.application.port.out.WorkflowStarter;
import com.lms.events.AvroEventSerde;
import com.lms.outbox.OutboxRepository;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.outbox.inbox.InboxStore;
import com.lms.outbox.jdbc.JdbcInboxStore;
import com.lms.outbox.jdbc.JdbcOutboxRepository;
import com.lms.security.callback.CallbackVerifier;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Clock;
import java.time.Duration;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires disbursement: outbox/inbox, Avro serde, callback verifier, resilience and the services. */
@Configuration
public class DisbursementConfig {

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
    public CallbackVerifier callbackVerifier(
            @Value("${lms.callback.secret:local-dev-callback-secret}") String secret,
            @Value("${lms.callback.tolerance-seconds:300}") long toleranceSeconds,
            Clock clock) {
        return CallbackVerifier.fromSecret(secret, clock, Duration.ofSeconds(toleranceSeconds));
    }

    @Bean
    public Retry railRetry(
            @Value("${lms.rail.retry.max-attempts:3}") int maxAttempts,
            @Value("${lms.rail.retry.wait-ms:200}") long waitMs) {
        return Retry.of(
                "rail",
                RetryConfig.custom()
                        .maxAttempts(maxAttempts)
                        .waitDuration(Duration.ofMillis(waitMs))
                        .build());
    }

    @Bean
    public CircuitBreaker railCircuitBreaker() {
        return CircuitBreaker.ofDefaults("rail");
    }

    @Bean
    public SagaTimeouts sagaTimeouts(
            @Value("${lms.disbursement.payout-timeout-seconds:1800}") long payoutTimeoutSeconds,
            @Value("${lms.disbursement.poll-rounds:5}") int pollRounds,
            @Value("${lms.disbursement.poll-backoff-seconds:30}") long pollBackoffSeconds,
            @Value("${lms.disbursement.max-attempts:3}") int maxAttempts,
            @Value("${lms.disbursement.retry-backoff-seconds:5}") long retryBackoffSeconds,
            @Value("${lms.disbursement.retry-multiplier:2.0}") double retryMultiplier,
            @Value("${lms.disbursement.activity-timeout-seconds:30}") long activityTimeoutSeconds) {
        return new SagaTimeouts(
                payoutTimeoutSeconds,
                pollRounds,
                pollBackoffSeconds,
                maxAttempts,
                retryBackoffSeconds,
                retryMultiplier,
                activityTimeoutSeconds);
    }

    @Bean
    public DisbursementService disbursementService(
            DisbursementRepository disbursements,
            PayoutAttemptRepository attempts,
            DisbursementEventPublisher publisher,
            RailClient rail,
            CompliancePolicy compliance,
            Clock clock,
            @Value("${lms.disbursement.callback-url:http://localhost:8105/callbacks/rail}")
                    String callbackUrl) {
        return new DisbursementService(
                disbursements, attempts, publisher, rail, compliance, clock, callbackUrl);
    }

    @Bean
    public RegisterDisbursementService registerDisbursementService(
            DisbursementRepository disbursements, WorkflowStarter workflows, Clock clock) {
        return new RegisterDisbursementService(disbursements, workflows, clock);
    }

    @Bean
    public CallbackClassifier callbackClassifier(
            DisbursementRepository disbursements, RailCallbackDedupStore dedup) {
        return new CallbackClassifier(disbursements, dedup);
    }

    @Bean
    public DisbursementQueryService disbursementQueryService(
            DisbursementRepository disbursements, PayoutAttemptRepository attempts) {
        return new DisbursementQueryService(disbursements, attempts);
    }
}
