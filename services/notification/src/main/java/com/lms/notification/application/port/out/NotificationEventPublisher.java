package com.lms.notification.application.port.out;

import com.lms.notification.domain.Channel;
import java.util.UUID;

/**
 * Publishes {@code notification.events} through the outbox (phase prompt task 5). Carries no PII —
 * only ids, template and channel (CLAUDE.md "Logging"). Keyed by notification id.
 */
public interface NotificationEventPublisher {

    void sent(UUID notificationId, String triggerEventId, String templateId, Channel channel);

    void failed(
            UUID notificationId,
            String triggerEventId,
            String templateId,
            Channel channel,
            int attempts,
            String failureCode);
}
