package com.lms.notification.application.port.out;

import com.lms.notification.domain.Channel;
import com.lms.notification.domain.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the notification log (phase prompt task 5). Deduplicates on {@code
 * (triggerEventId, templateId, channel)} so replaying an event sends no second message, and tracks
 * each notification's status, attempts, held body and release time.
 */
public interface NotificationLogRepository {

    /**
     * Reserve a {@code QUEUED} row for {@code (triggerEventId, templateId, channel)} unless one
     * already exists. {@code releaseAt} is non-null when the message is held for quiet hours.
     * Returns the new notification id, or empty if it was a duplicate (so no second message).
     */
    Optional<UUID> createIfAbsent(
            String triggerEventId,
            String templateId,
            Channel channel,
            String recipientRef,
            String body,
            Instant releaseAt,
            Instant now);

    /** Update status (and attempts / failure code). Clears the release time once delivered. */
    void updateStatus(UUID id, NotificationStatus status, int attempts, String failureCode);

    /** Held messages (QUEUED with a release time at or before {@code now}). */
    List<Held> findDue(Instant now);

    /** Notifications, newest first, optionally filtered by status (admin log). */
    List<Row> list(Optional<String> status);

    /** A held message to release. */
    record Held(
            UUID id,
            String triggerEventId,
            String templateId,
            Channel channel,
            String recipientRef,
            String body) {}

    /** A log row for the admin view. */
    record Row(
            UUID id,
            String triggerEventId,
            String templateId,
            Channel channel,
            NotificationStatus status,
            int attempts,
            String failureCode,
            Instant createdAt) {}
}
