package com.lms.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BlindIndexTest {

    private static byte[] key(byte fill) {
        byte[] k = new byte[32];
        java.util.Arrays.fill(k, fill);
        return k;
    }

    private final BlindIndex index = new BlindIndex(key((byte) 1));

    @Test
    void isDeterministicForTheSameValueAndKey() {
        assertThat(index.compute("ABCDE1234F")).isEqualTo(index.compute("ABCDE1234F"));
    }

    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(index.compute("  abcde1234f ")).isEqualTo(index.compute("ABCDE1234F"));
    }

    @Test
    void differsForDifferentValues() {
        assertThat(index.compute("ABCDE1234F")).isNotEqualTo(index.compute("ABCDE1234G"));
    }

    @Test
    void differsUnderADifferentKey() {
        BlindIndex other = new BlindIndex(key((byte) 2));
        assertThat(index.compute("ABCDE1234F")).isNotEqualTo(other.compute("ABCDE1234F"));
    }

    @Test
    void isHexSha256Length() {
        assertThat(index.compute("x")).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void rejectsShortKey() {
        assertThatThrownBy(() -> new BlindIndex(new byte[8]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
