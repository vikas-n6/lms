package com.lms.notification.domain;

/**
 * Formats a non-PII notification line from an event's envelope (Phase 1 stub). Only the event type
 * and aggregate reference appear — never payload PII (CLAUDE.md "Logging").
 */
public final class NotificationFormatter {

    private NotificationFormatter() {}

    public static String line(String eventType, String aggregateType, String aggregateId) {
        return "notification: %s for %s %s".formatted(eventType, aggregateType, aggregateId);
    }
}
