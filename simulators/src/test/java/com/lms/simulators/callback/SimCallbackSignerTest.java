package com.lms.simulators.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.security.callback.CallbackVerifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Pins the simulators' {@link SimCallbackSigner} to lms-security's {@link CallbackVerifier}: a
 * header signed here must verify there, so the two implementations cannot drift (phase prompt: no
 * callback accepted without a valid signature). Pure JUnit — no Spring context — so lms-security on
 * the test classpath never starts Spring Security against the mock endpoints.
 */
class SimCallbackSignerTest {

    private static final String SECRET = "local-dev-callback-secret";
    private static final Instant NOW = Instant.parse("2026-11-13T06:00:00Z");

    private final SimCallbackSigner signer = new SimCallbackSigner(SECRET);

    @Test
    void simSignatureVerifiesWithSecurityVerifier() {
        String body = "{\"railRef\":\"RAIL-1\",\"status\":\"SUCCESS\",\"utr\":\"UTR-9\"}";
        String header = signer.sign(NOW, body);

        CallbackVerifier verifier =
                CallbackVerifier.fromSecret(
                        SECRET, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
        assertThat(verifier.isValid(header, body)).isTrue();
    }

    @Test
    void differentSecretDoesNotVerify() {
        String body = "payload";
        String header = signer.sign(NOW, body);

        CallbackVerifier verifier =
                CallbackVerifier.fromSecret(
                        "other", Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
        assertThat(verifier.isValid(header, body)).isFalse();
    }

    @Test
    void emptySecretIsRejected() {
        assertThatThrownBy(() -> new SimCallbackSigner(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
