package com.lms.simulators.sms;

import java.time.Instant;

/**
 * A stored outbound SMS. Tests and the Admin UI read the outbox to pick up OTPs.
 *
 * @param id message id
 * @param to destination phone
 * @param body message text (may contain an OTP)
 * @param sentAt UTC send instant from the injected Clock
 */
public record SmsMessage(String id, String to, String body, Instant sentAt) {}
