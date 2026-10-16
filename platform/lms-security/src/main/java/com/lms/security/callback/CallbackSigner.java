package com.lms.security.callback;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs asynchronous simulator callbacks so a receiver can prove authenticity before acting on them
 * (docs/architecture.md: "no unsigned callbacks"; ADR 0009). The signature is HMAC-SHA256 over the
 * canonical message {@code epochMillis + "." + rawBody}; the header it produces, {@code
 * t=<epochMillis>,v1=<base64(mac)>}, binds a timestamp so a receiver can also reject stale replays
 * within a configured skew window.
 *
 * <p>Pure JDK crypto (no Spring, no external dependency). The shared secret comes from
 * configuration / the environment, never from code or Git (CLAUDE.md "no secrets in Git").
 */
public final class CallbackSigner {

    static final String HEADER_NAME = "X-Callback-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public CallbackSigner(byte[] secret) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length == 0) {
            throw new IllegalArgumentException("callback signing secret must not be empty");
        }
        this.secret = secret.clone();
    }

    /** Build from a shared-secret string (as supplied via an environment variable). */
    public static CallbackSigner fromSecret(String secret) {
        Objects.requireNonNull(secret, "secret");
        return new CallbackSigner(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** The header value a caller attaches to a signed callback request. */
    public String sign(Instant timestamp, String body) {
        Objects.requireNonNull(timestamp, "timestamp");
        long epochMillis = timestamp.toEpochMilli();
        return "t=" + epochMillis + ",v1=" + macBase64(epochMillis, body);
    }

    /** Base64 HMAC of the canonical message for a given timestamp and body. */
    String macBase64(long epochMillis, String body) {
        Objects.requireNonNull(body, "body");
        byte[] message = (epochMillis + "." + body).getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(hmac(message));
    }

    private byte[] hmac(byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("callback signing failed", e);
        }
    }
}
