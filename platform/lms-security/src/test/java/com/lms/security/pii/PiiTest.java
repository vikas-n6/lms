package com.lms.security.pii;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiTest {

    @Test
    void masksPhoneKeepingLastTwo() {
        assertThat(Pii.maskPhone("9876543210")).isEqualTo("********10");
    }

    @Test
    void masksAccountKeepingLastFour() {
        assertThat(Pii.maskAccount("00112233445566")).isEqualTo("**********5566");
    }

    @Test
    void masksPanKeepingLastFour() {
        assertThat(Pii.maskPan("ABCDE1234F")).isEqualTo("******234F");
    }

    @Test
    void masksEmailKeepingFirstCharAndDomain() {
        assertThat(Pii.maskEmail("asha.rao@example.com")).isEqualTo("a*******@example.com");
    }

    @Test
    void masksMalformedEmailEntirely() {
        assertThat(Pii.maskEmail("notanemail")).isEqualTo("**********");
    }

    @Test
    void isNullSafe() {
        assertThat(Pii.maskPhone(null)).isNull();
        assertThat(Pii.maskEmail(null)).isNull();
    }

    @Test
    void shortValuesAreFullyMaskedWhenShorterThanVisibleWindow() {
        assertThat(Pii.maskKeepingLast("1", 4)).isEqualTo("1");
        assertThat(Pii.maskKeepingLast("", 4)).isEmpty();
    }
}
