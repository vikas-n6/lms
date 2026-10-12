package com.lms.customer.adapters.out.otp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecureRandomOtpGeneratorTest {

    private final SecureRandomOtpGenerator generator = new SecureRandomOtpGenerator();

    @Test
    void generatesSixDigitCodes() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.generate()).hasSize(6).matches("[0-9]{6}");
        }
    }
}
