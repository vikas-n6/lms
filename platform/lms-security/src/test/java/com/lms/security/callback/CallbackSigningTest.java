package com.lms.security.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Signed-callback round-trips and rejection cases (docs/architecture.md: callbacks bypass JWT but
 * require a valid signature; phase prompt "Do not accept any callback without a valid signature").
 * Pure HMAC-SHA256 over {@code epochMillis + "." + body}; JDK crypto only, so it runs on JDK 25
 * without Docker.
 */
class CallbackSigningTest {

    private static final byte[] SECRET = "top-secret-shared-key".getBytes();
    private static final Instant NOW = Instant.parse("2026-11-13T06:00:00Z");

    private static Clock fixed(Instant at) {
        return Clock.fixed(at, ZoneOffset.UTC);
    }

    private final CallbackSigner signer = new CallbackSigner(SECRET);

    @Test
    void signThenVerifyRoundTrips() {
        String body = "{\"railRef\":\"RAIL-1\",\"status\":\"SUCCESS\"}";
        String header = signer.sign(NOW, body);

        CallbackVerifier verifier = new CallbackVerifier(SECRET, fixed(NOW), Duration.ofMinutes(5));
        assertThat(verifier.isValid(header, body)).isTrue();
    }

    @Test
    void tamperedBodyIsRejected() {
        String body = "{\"railRef\":\"RAIL-1\",\"status\":\"SUCCESS\"}";
        String header = signer.sign(NOW, body);

        CallbackVerifier verifier = new CallbackVerifier(SECRET, fixed(NOW), Duration.ofMinutes(5));
        assertThat(verifier.isValid(header, "{\"railRef\":\"RAIL-1\",\"status\":\"FAILED\"}"))
                .isFalse();
    }

    @Test
    void wrongSecretIsRejected() {
        String body = "payload";
        String header = signer.sign(NOW, body);

        CallbackVerifier verifier =
                new CallbackVerifier("other-key".getBytes(), fixed(NOW), Duration.ofMinutes(5));
        assertThat(verifier.isValid(header, body)).isFalse();
    }

    @Test
    void staleTimestampBeyondToleranceIsRejected() {
        String body = "payload";
        String header = signer.sign(NOW, body);

        Clock later = fixed(NOW.plus(Duration.ofMinutes(6)));
        CallbackVerifier verifier = new CallbackVerifier(SECRET, later, Duration.ofMinutes(5));
        assertThat(verifier.isValid(header, body)).isFalse();
    }

    @Test
    void futureTimestampBeyondToleranceIsRejected() {
        String body = "payload";
        String header = signer.sign(NOW, body);

        Clock earlier = fixed(NOW.minus(Duration.ofMinutes(6)));
        CallbackVerifier verifier = new CallbackVerifier(SECRET, earlier, Duration.ofMinutes(5));
        assertThat(verifier.isValid(header, body)).isFalse();
    }

    @Test
    void withinToleranceIsAccepted() {
        String body = "payload";
        String header = signer.sign(NOW, body);

        Clock slightlyLater = fixed(NOW.plus(Duration.ofMinutes(4)));
        CallbackVerifier verifier =
                new CallbackVerifier(SECRET, slightlyLater, Duration.ofMinutes(5));
        assertThat(verifier.isValid(header, body)).isTrue();
    }

    @Test
    void malformedHeaderIsRejectedNotThrown() {
        CallbackVerifier verifier = new CallbackVerifier(SECRET, fixed(NOW), Duration.ofMinutes(5));
        assertThat(verifier.isValid(null, "x")).isFalse();
        assertThat(verifier.isValid("", "x")).isFalse();
        assertThat(verifier.isValid("garbage", "x")).isFalse();
        assertThat(verifier.isValid("t=notanumber,v1=abc", "x")).isFalse();
        assertThat(verifier.isValid("t=123", "x")).isFalse();
        assertThat(verifier.isValid("v1=abc", "x")).isFalse();
    }

    @Test
    void verifyThrowsOnInvalidSignature() {
        CallbackVerifier verifier = new CallbackVerifier(SECRET, fixed(NOW), Duration.ofMinutes(5));
        assertThatThrownBy(() -> verifier.verify("t=1,v1=bad", "body"))
                .isInstanceOf(InvalidCallbackSignatureException.class);
    }

    @Test
    void emptySecretIsRejected() {
        assertThatThrownBy(() -> new CallbackSigner(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
