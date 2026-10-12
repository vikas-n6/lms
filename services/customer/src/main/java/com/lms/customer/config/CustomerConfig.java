package com.lms.customer.config;

import com.lms.customer.application.OtpCodeGenerator;
import com.lms.customer.application.OtpService;
import com.lms.customer.application.port.out.OtpStore;
import com.lms.customer.application.port.out.SmsSender;
import com.lms.events.AvroEventSerde;
import com.lms.outbox.OutboxRepository;
import com.lms.outbox.jdbc.JdbcOutboxRepository;
import com.lms.security.crypto.BlindIndex;
import com.lms.security.crypto.FieldCipher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires the customer service: outbox, serde, column crypto, OTP and Resilience4j. */
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

    // Dev defaults are exactly 32 bytes (AES-256); override from a secret manager in real envs.
    @Bean
    public FieldCipher idNumberCipher(
            @Value("${lms.security.id-number-key:0123456789abcdef0123456789abcdef}") String key) {
        return new FieldCipher(key.getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    public FieldCipher accountNumberCipher(
            @Value("${lms.security.account-number-key:abcdef9876543210abcdef9876543210}")
                    String key) {
        return new FieldCipher(key.getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    public BlindIndex idNumberBlindIndex(
            @Value("${lms.security.id-number-index-key:fedcba9876543210fedcba9876543210}")
                    String key) {
        return new BlindIndex(key.getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    public BlindIndex accountNumberBlindIndex(
            @Value("${lms.security.account-number-index-key:0f1e2d3c4b5a69780f1e2d3c4b5a6978}")
                    String key) {
        return new BlindIndex(key.getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    public OtpService otpService(
            OtpStore otpStore,
            SmsSender smsSender,
            OtpCodeGenerator generator,
            @Value("${lms.customer.otp-ttl-minutes:5}") long ttlMinutes) {
        return new OtpService(otpStore, smsSender, generator, Duration.ofMinutes(ttlMinutes));
    }

    private static Retry retry(String name) {
        return Retry.of(
                name,
                RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(200)).build());
    }

    @Bean
    public Retry kycRetry() {
        return retry("kyc");
    }

    @Bean
    public CircuitBreaker kycCircuitBreaker() {
        return CircuitBreaker.ofDefaults("kyc");
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
