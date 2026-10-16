package com.lms.security.callback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Verifies the {@link CallbackSigner} header on an inbound callback before any effect is applied
 * (phase prompt: "Do not accept any callback without a valid signature"). A callback is valid only
 * when the HMAC matches (constant-time compare) <em>and</em> its timestamp is within {@code
 * tolerance} of the injected {@link Clock}, which bounds replay.
 *
 * <p>Any malformed header is a rejection, never an exception, so a bad caller cannot crash the
 * endpoint; {@link #verify} is the throwing convenience for controllers that want a 401/403
 * mapping.
 */
public final class CallbackVerifier {

    private final CallbackSigner signer;
    private final Clock clock;
    private final Duration tolerance;

    public CallbackVerifier(byte[] secret, Clock clock, Duration tolerance) {
        this.signer = new CallbackSigner(secret);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
        if (tolerance.isNegative()) {
            throw new IllegalArgumentException("tolerance must not be negative");
        }
    }

    public static CallbackVerifier fromSecret(String secret, Clock clock, Duration tolerance) {
        return new CallbackVerifier(secret.getBytes(StandardCharsets.UTF_8), clock, tolerance);
    }

    /** True when the signature matches and the timestamp is within tolerance of now. */
    public boolean isValid(String header, String body) {
        if (header == null || body == null || header.isBlank()) {
            return false;
        }
        Long timestamp = null;
        String provided = null;
        for (String part : header.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if ("t".equals(key)) {
                timestamp = parseLong(value);
            } else if ("v1".equals(key)) {
                provided = value;
            }
        }
        if (timestamp == null || provided == null) {
            return false;
        }
        long skewMillis = Math.abs(clock.instant().toEpochMilli() - timestamp);
        if (skewMillis > tolerance.toMillis()) {
            return false;
        }
        String expected = signer.macBase64(timestamp, body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    /** Throwing variant for controllers that map an invalid signature to a 4xx problem detail. */
    public void verify(String header, String body) {
        if (!isValid(header, body)) {
            throw new InvalidCallbackSignatureException();
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
