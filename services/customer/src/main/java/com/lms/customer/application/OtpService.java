package com.lms.customer.application;

import com.lms.customer.application.port.out.OtpStore;
import com.lms.customer.application.port.out.SmsSender;
import java.time.Duration;
import java.util.Objects;

/**
 * Issues and verifies one-time passwords for phone verification and OTP e-sign (phase prompt S3).
 * The code is stored with a TTL and sent through the SMS simulator; verification is single-use. The
 * code itself is the only sensitive content and never appears in logs.
 */
public class OtpService {

    private final OtpStore store;
    private final SmsSender sms;
    private final OtpCodeGenerator generator;
    private final Duration ttl;

    public OtpService(OtpStore store, SmsSender sms, OtpCodeGenerator generator, Duration ttl) {
        this.store = Objects.requireNonNull(store, "store");
        this.sms = Objects.requireNonNull(sms, "sms");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    /** Generate a code, store it under {@code key} for the TTL, and text it to {@code phone}. */
    public void issue(String key, String phone) {
        String code = generator.generate();
        store.store(key, code, ttl);
        sms.send(phone, "Your LMS verification code is " + code);
    }

    /** Verify and consume a code. Returns false when absent, expired or mismatched. */
    public boolean verify(String key, String code) {
        return code != null && store.consume(key, code);
    }
}
