package com.lms.simulators.callback;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs the asynchronous callbacks the simulators deliver (rail, mandate, payments), so the
 * receiving service can reject anything unsigned (phase prompt: "Do not accept any callback without
 * a valid signature").
 *
 * <p>The format is identical to {@code com.lms.security.callback.CallbackSigner}: header {@code
 * t=<epochMillis>,v1=<base64(HmacSHA256(secret, epochMillis + "." + rawBody))>}. The two
 * implementations are deliberately kept in step by a cross-verification test that signs here and
 * verifies with lms-security's {@code CallbackVerifier}. The simulators stay free of Spring
 * Security on their runtime classpath (which would otherwise lock down these open mock endpoints),
 * so lms- security is a test-only dependency here rather than a shared runtime module.
 */
public final class SimCallbackSigner {

    public static final String HEADER_NAME = "X-Callback-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public SimCallbackSigner(String secret) {
        Objects.requireNonNull(secret, "secret");
        if (secret.isEmpty()) {
            throw new IllegalArgumentException("callback signing secret must not be empty");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** The header value to attach to a signed callback request for the given raw body. */
    public String sign(Instant timestamp, String body) {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(body, "body");
        long epochMillis = timestamp.toEpochMilli();
        byte[] message = (epochMillis + "." + body).getBytes(StandardCharsets.UTF_8);
        return "t=" + epochMillis + ",v1=" + Base64.getEncoder().encodeToString(hmac(message));
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
