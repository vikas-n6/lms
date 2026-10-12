package com.lms.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class FieldCipherTest {

    // A fixed 256-bit test key (never a real key; real keys come from the environment).
    private static final byte[] KEY = new byte[32];

    private final FieldCipher cipher = new FieldCipher(KEY);

    @Test
    void roundTripsPlaintext() {
        String secret = "ABCDE1234F"; // PAN-like id
        assertThat(cipher.decrypt(cipher.encrypt(secret))).isEqualTo(secret);
    }

    @Test
    void producesDifferentCiphertextEachTimeButDecryptsSame() {
        String secret = "123456789012";
        String a = cipher.encrypt(secret);
        String b = cipher.encrypt(secret);
        assertThat(a).isNotEqualTo(b); // fresh IV each time (not deterministic)
        assertThat(cipher.decrypt(a)).isEqualTo(secret);
        assertThat(cipher.decrypt(b)).isEqualTo(secret);
    }

    @Test
    void tamperingIsDetected() {
        byte[] token = Base64.getDecoder().decode(cipher.encrypt("sensitive"));
        token[token.length - 1] ^= 0x01; // flip a tag bit
        String tampered = Base64.getEncoder().encodeToString(token);
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyFailsToDecrypt() {
        String token = cipher.encrypt("sensitive");
        byte[] otherKey = new byte[32];
        otherKey[0] = 7;
        assertThatThrownBy(() -> new FieldCipher(otherKey).decrypt(token))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsBadKeyLength() {
        assertThatThrownBy(() -> new FieldCipher(new byte[10]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsFromBase64Key() {
        FieldCipher fromB64 = FieldCipher.fromBase64Key(Base64.getEncoder().encodeToString(KEY));
        assertThat(fromB64.decrypt(fromB64.encrypt("x"))).isEqualTo("x");
    }
}
