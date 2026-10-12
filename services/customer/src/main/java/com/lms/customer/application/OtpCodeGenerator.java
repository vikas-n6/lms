package com.lms.customer.application;

/** Generates a fresh OTP code. An interface so tests can inject a fixed code. */
@FunctionalInterface
public interface OtpCodeGenerator {
    String generate();
}
