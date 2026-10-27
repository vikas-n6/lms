package com.lms.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeyRingTest {

    private static String key(byte b) {
        byte[] k = new byte[32];
        java.util.Arrays.fill(k, b);
        return Base64.getEncoder().encodeToString(k);
    }

    private static KeyRing ring(String currentId, Map<String, String> keys) {
        return KeyRing.fromBase64(keys, currentId);
    }

    @Test
    void encryptsWithCurrentKeyAndTokenCarriesTheKeyId() {
        KeyRing r = ring("v1", Map.of("v1", key((byte) 1)));
        String token = r.encrypt("4111111111111111");
        assertThat(token).startsWith("v1:");
        assertThat(r.decrypt(token)).isEqualTo("4111111111111111");
        assertThat(r.isCurrent(token)).isTrue();
    }

    @Test
    void decryptsTokensWrittenUnderAnOlderKeyAfterRotation() {
        KeyRing before = ring("v1", Map.of("v1", key((byte) 1)));
        String oldToken = before.encrypt("sensitive");

        // Introduce v2 as the new current key while keeping v1 for reads.
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("v1", key((byte) 1));
        keys.put("v2", key((byte) 2));
        KeyRing after = ring("v2", keys);

        assertThat(after.decrypt(oldToken)).isEqualTo("sensitive"); // still readable
        assertThat(after.isCurrent(oldToken)).isFalse();
    }

    @Test
    void rotateReEncryptsToTheCurrentKeyAndIsIdempotent() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("v1", key((byte) 1));
        keys.put("v2", key((byte) 2));
        KeyRing r = ring("v2", keys);

        KeyRing v1Only = ring("v1", Map.of("v1", key((byte) 1)));
        String oldToken = v1Only.encrypt("pan-12345");

        String rotated = r.rotate(oldToken);
        assertThat(rotated).startsWith("v2:");
        assertThat(r.decrypt(rotated)).isEqualTo("pan-12345");

        // Rotating an already-current token is a no-op (same token returned).
        assertThat(r.rotate(rotated)).isEqualTo(rotated);
    }

    @Test
    void unknownKeyIdIsRejected() {
        KeyRing r = ring("v2", Map.of("v2", key((byte) 2)));
        assertThatThrownBy(() -> r.decrypt("v9:AAAA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v9");
    }

    @Test
    void tamperedCiphertextFailsAuthentication() {
        KeyRing r = ring("v1", Map.of("v1", key((byte) 1)));
        String token = r.encrypt("secret");
        String tampered =
                token.substring(0, token.length() - 2) + (token.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> r.decrypt(tampered)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void currentKeyMustBePresentInTheRing() {
        assertThatThrownBy(() -> ring("v3", Map.of("v1", key((byte) 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
