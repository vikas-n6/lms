package com.lms.notification.application;

import com.lms.notification.application.port.out.ChannelGateway;
import com.lms.notification.application.port.out.ChannelGateway.ChannelDeliveryException;
import com.lms.notification.application.port.out.ConsentPolicy;
import com.lms.notification.application.port.out.NotificationEventPublisher;
import com.lms.notification.application.port.out.NotificationLogRepository;
import com.lms.notification.application.port.out.NotificationLogRepository.Held;
import com.lms.notification.application.port.out.TemplateCatalog;
import com.lms.notification.domain.Channel;
import com.lms.notification.domain.NotificationStatus;
import com.lms.notification.domain.NotificationTemplate;
import com.lms.notification.domain.QuietHours;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a trigger event into channel messages (phase prompt task 5). For each configured template
 * it deduplicates on {@code (triggerEventId, templateId, channel)} (a replay sends nothing),
 * suppresses when consent is withheld, holds messages created during quiet hours for release the
 * next morning, and otherwise delivers with bounded retries, ending {@code FAILED} after the
 * configured attempts.
 */
public class NotificationService {

    private final TemplateCatalog templates;
    private final NotificationLogRepository log;
    private final ChannelGateway gateway;
    private final ConsentPolicy consent;
    private final NotificationEventPublisher publisher;
    private final QuietHours quietHours;
    private final Backoff backoff;
    private final Clock clock;
    private final int maxAttempts;

    public NotificationService(
            TemplateCatalog templates,
            NotificationLogRepository log,
            ChannelGateway gateway,
            ConsentPolicy consent,
            NotificationEventPublisher publisher,
            QuietHours quietHours,
            Backoff backoff,
            Clock clock,
            int maxAttempts) {
        this.templates = Objects.requireNonNull(templates);
        this.log = Objects.requireNonNull(log);
        this.gateway = Objects.requireNonNull(gateway);
        this.consent = Objects.requireNonNull(consent);
        this.publisher = Objects.requireNonNull(publisher);
        this.quietHours = Objects.requireNonNull(quietHours);
        this.backoff = Objects.requireNonNull(backoff);
        this.clock = Objects.requireNonNull(clock);
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
    }

    /** Fan a trigger event out to its configured templates/channels. */
    public void notify(
            String triggerEventId,
            String eventType,
            String recipientRef,
            Map<String, String> placeholders) {
        Instant now = clock.instant();
        boolean quiet = quietHours.isQuiet(now);
        Instant releaseAt = quiet ? quietHours.releaseAt(now) : null;

        for (NotificationTemplate template : templates.templatesFor(eventType)) {
            Channel channel = template.channel();
            String body = template.render(placeholders);
            Optional<UUID> created =
                    log.createIfAbsent(
                            triggerEventId,
                            template.id(),
                            channel,
                            recipientRef,
                            body,
                            releaseAt,
                            now);
            if (created.isEmpty()) {
                continue; // duplicate (triggerEventId, templateId, channel): no second message
            }
            UUID id = created.get();
            if (!consent.allowed(recipientRef, channel)) {
                log.updateStatus(id, NotificationStatus.SUPPRESSED, 0, null);
                continue;
            }
            if (quiet) {
                continue; // stays QUEUED, released by releaseDue the next morning
            }
            deliver(id, triggerEventId, template.id(), channel, recipientRef, body);
        }
    }

    /** Release held messages whose quiet-hours window has ended (scheduled job). */
    public int releaseDue(Instant now) {
        int released = 0;
        for (Held held : log.findDue(now)) {
            deliver(
                    held.id(),
                    held.triggerEventId(),
                    held.templateId(),
                    held.channel(),
                    held.recipientRef(),
                    held.body());
            released++;
        }
        return released;
    }

    private void deliver(
            UUID id,
            String triggerEventId,
            String templateId,
            Channel channel,
            String recipient,
            String body) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                gateway.send(channel, recipient, body);
                log.updateStatus(id, NotificationStatus.SENT, attempt, null);
                publisher.sent(id, triggerEventId, templateId, channel);
                return;
            } catch (ChannelDeliveryException e) {
                if (attempt >= maxAttempts) {
                    log.updateStatus(id, NotificationStatus.FAILED, attempt, e.failureCode());
                    publisher.failed(
                            id, triggerEventId, templateId, channel, attempt, e.failureCode());
                    return;
                }
                backoff.pause(attempt);
            }
        }
    }
}
