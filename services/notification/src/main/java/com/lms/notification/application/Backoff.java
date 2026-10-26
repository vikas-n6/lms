package com.lms.notification.application;

/**
 * Pauses between channel retry attempts (phase prompt task 5: "retry with backoff"). The production
 * bean sleeps with exponential backoff; unit tests inject a no-op so retries are deterministic and
 * fast.
 */
@FunctionalInterface
public interface Backoff {

    void pause(int attempt);
}
