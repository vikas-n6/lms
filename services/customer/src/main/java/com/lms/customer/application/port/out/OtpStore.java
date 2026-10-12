package com.lms.customer.application.port.out;

import java.time.Duration;

/**
 * Short-lived OTP storage (phase prompt S3): the production adapter is Redis with a TTL, backed by
 * nothing else because an OTP is ephemeral. The key is scoped per purpose and subject (e.g. {@code
 * lms:customer:otp:<customerId>}).
 */
public interface OtpStore {

    /** Store {@code code} under {@code key} for {@code ttl}, replacing any existing code. */
    void store(String key, String code, Duration ttl);

    /**
     * Verify {@code code} against the stored value and invalidate it (single-use). Returns false
     * when absent, expired or mismatched.
     */
    boolean consume(String key, String code);
}
