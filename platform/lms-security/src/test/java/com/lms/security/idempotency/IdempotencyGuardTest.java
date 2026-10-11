package com.lms.security.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IdempotencyGuardTest {

    private final IdempotencyStore store = new InMemoryIdempotencyStore();
    private final IdempotencyGuard guard = new IdempotencyGuard(store);

    private static byte[] body(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void readRequestsProceedWithoutAKey() {
        assertThat(guard.evaluate("GET", null, IdempotencyGuard.fingerprint(body(""))))
                .isInstanceOf(IdempotencyDecision.Proceed.class);
    }

    @Test
    void stateChangingRequestWithoutKeyIsMissingKey() {
        assertThat(guard.evaluate("POST", null, IdempotencyGuard.fingerprint(body("{}"))))
                .isInstanceOf(IdempotencyDecision.MissingKey.class);
    }

    @Test
    void firstTimeKeyProceeds() {
        assertThat(guard.evaluate("POST", "key-1", IdempotencyGuard.fingerprint(body("{}"))))
                .isInstanceOf(IdempotencyDecision.Proceed.class);
    }

    @Test
    void sameKeySameBodyReplaysStoredResponse() {
        String fp = IdempotencyGuard.fingerprint(body("{\"a\":1}"));
        StoredResponse stored = new StoredResponse(201, body("created"), fp);
        store.putIfAbsent("key-2", stored, Duration.ofHours(24));

        IdempotencyDecision decision = guard.evaluate("POST", "key-2", fp);
        assertThat(decision).isInstanceOf(IdempotencyDecision.Replay.class);
        assertThat(((IdempotencyDecision.Replay) decision).response()).isEqualTo(stored);
    }

    @Test
    void sameKeyDifferentBodyIsConflict() {
        String originalFp = IdempotencyGuard.fingerprint(body("{\"a\":1}"));
        store.putIfAbsent(
                "key-3",
                new StoredResponse(201, body("created"), originalFp),
                Duration.ofHours(24));

        String differentFp = IdempotencyGuard.fingerprint(body("{\"a\":2}"));
        assertThat(guard.evaluate("POST", "key-3", differentFp))
                .isInstanceOf(IdempotencyDecision.Conflict.class);
    }

    @Test
    void fingerprintIsStableAndBodyDependent() {
        assertThat(IdempotencyGuard.fingerprint(body("x")))
                .isEqualTo(IdempotencyGuard.fingerprint(body("x")))
                .isNotEqualTo(IdempotencyGuard.fingerprint(body("y")));
    }

    @Test
    void storedResponseBodyIsDefensivelyCopied() {
        byte[] original = body("abc");
        StoredResponse stored = new StoredResponse(200, original, "fp");
        original[0] = 'z';
        assertThat(stored.body()).isEqualTo(body("abc"));
    }
}
