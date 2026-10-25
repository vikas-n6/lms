package com.lms.repayment.config;

import com.lms.events.AvroEventSerde;
import com.lms.outbox.OutboxRepository;
import com.lms.outbox.jdbc.JdbcOutboxRepository;
import com.lms.repayment.application.AutoDebitService;
import com.lms.repayment.application.CreatePaymentLinkService;
import com.lms.repayment.application.RecordRepaymentService;
import com.lms.repayment.application.RegisterMandateService;
import com.lms.repayment.application.ReverseRepaymentService;
import com.lms.repayment.application.port.out.CallbackDedupStore;
import com.lms.repayment.application.port.out.DueInstalmentsPort;
import com.lms.repayment.application.port.out.MandateClient;
import com.lms.repayment.application.port.out.MandateRepository;
import com.lms.repayment.application.port.out.PaymentsClient;
import com.lms.repayment.application.port.out.RepaymentEventPublisher;
import com.lms.repayment.application.port.out.RepaymentRepository;
import com.lms.security.callback.CallbackVerifier;
import java.time.Clock;
import java.time.Duration;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires repayment: outbox, Avro serde, callback verifier and the application services. */
@Configuration
public class RepaymentConfig {

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
    public CallbackVerifier callbackVerifier(
            @Value("${lms.callback.secret:local-dev-callback-secret}") String secret,
            @Value("${lms.callback.tolerance-seconds:300}") long toleranceSeconds,
            Clock clock) {
        return CallbackVerifier.fromSecret(secret, clock, Duration.ofSeconds(toleranceSeconds));
    }

    @Bean
    public RegisterMandateService registerMandateService(
            MandateRepository mandates,
            MandateClient client,
            RepaymentEventPublisher publisher,
            Clock clock,
            @Value("${lms.repayment.mandate-callback-url:http://localhost:8106/callbacks/mandates}")
                    String callbackUrl) {
        return new RegisterMandateService(mandates, client, publisher, clock, callbackUrl);
    }

    @Bean
    public RecordRepaymentService recordRepaymentService(
            RepaymentRepository repayments,
            RepaymentEventPublisher publisher,
            CallbackDedupStore dedup,
            Clock clock) {
        return new RecordRepaymentService(repayments, publisher, dedup, clock);
    }

    @Bean
    public ReverseRepaymentService reverseRepaymentService(
            RepaymentRepository repayments,
            RepaymentEventPublisher publisher,
            CallbackDedupStore dedup,
            Clock clock) {
        return new ReverseRepaymentService(repayments, publisher, dedup, clock);
    }

    @Bean
    public CreatePaymentLinkService createPaymentLinkService(
            PaymentsClient payments,
            @Value(
                            "${lms.repayment.payments-callback-url:http://localhost:8106/callbacks/payments}")
                    String callbackUrl) {
        return new CreatePaymentLinkService(payments, callbackUrl);
    }

    @Bean
    public AutoDebitService autoDebitService(
            DueInstalmentsPort due,
            MandateRepository mandates,
            MandateClient client,
            @Value("${lms.repayment.mandate-callback-url:http://localhost:8106/callbacks/mandates}")
                    String callbackUrl) {
        return new AutoDebitService(due, mandates, client, callbackUrl);
    }
}
