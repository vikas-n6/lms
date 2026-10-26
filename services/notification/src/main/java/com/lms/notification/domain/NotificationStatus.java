package com.lms.notification.domain;

/**
 * The lifecycle of a notification (phase prompt task 5): {@code QUEUED} (accepted, perhaps held for
 * quiet hours), {@code SENT}, {@code FAILED} (retries exhausted), {@code SUPPRESSED} (consent
 * withheld).
 */
public enum NotificationStatus {
    QUEUED,
    SENT,
    FAILED,
    SUPPRESSED
}
