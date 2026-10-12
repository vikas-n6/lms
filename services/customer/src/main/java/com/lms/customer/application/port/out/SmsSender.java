package com.lms.customer.application.port.out;

/**
 * Sends an SMS through the SMS simulator (phase prompt S3). The adapter posts to {@code
 * /sim/sms/send}; tests substitute a fake. Message bodies must never contain PII beyond the OTP.
 */
public interface SmsSender {
    void send(String phone, String message);
}
