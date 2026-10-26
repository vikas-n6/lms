package com.lms.notification.adapters.out.events;

import com.lms.contracts.notification.NotificationFailed;
import com.lms.contracts.notification.NotificationSent;
import com.lms.events.EventHeaders;
import com.lms.events.EventMeta;
import com.lms.events.EventMetadataMapper;
import com.lms.events.Uuid7;
import com.lms.notification.application.port.out.NotificationEventPublisher;
import com.lms.notification.domain.Channel;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.stereotype.Component;

/**
 * Writes {@code notification.events} to the outbox in the same transaction as the log update (never
 * straight to Kafka — docs/architecture.md). Keyed by notification id. Carries no PII.
 */
@Component
public class OutboxNotificationEventPublisher implements NotificationEventPublisher {

    private static final String TOPIC = "notification.events";
    private static final String AGGREGATE = "notification";
    private static final String PRODUCER = "notification";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outbox;
    private final Serializer<Object> serializer;
    private final Clock clock;

    public OutboxNotificationEventPublisher(
            OutboxRepository outbox, Serializer<Object> serializer, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void sent(
            UUID notificationId, String triggerEventId, String templateId, Channel channel) {
        String id = notificationId.toString();
        EventMeta meta = meta("notification.sent", id);
        NotificationSent event =
                NotificationSent.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setNotificationId(id)
                        .setTriggerEventId(triggerEventId == null ? "" : triggerEventId)
                        .setTemplateId(templateId)
                        .setChannel(channel.name())
                        .setSentAt(clock.instant())
                        .build();
        append(meta, id, event);
    }

    @Override
    public void failed(
            UUID notificationId,
            String triggerEventId,
            String templateId,
            Channel channel,
            int attempts,
            String failureCode) {
        String id = notificationId.toString();
        EventMeta meta = meta("notification.failed", id);
        NotificationFailed event =
                NotificationFailed.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setNotificationId(id)
                        .setTriggerEventId(triggerEventId == null ? "" : triggerEventId)
                        .setTemplateId(templateId)
                        .setChannel(channel.name())
                        .setAttempts(attempts)
                        .setFailureCode(failureCode == null ? "UNKNOWN" : failureCode)
                        .setFailedAt(clock.instant())
                        .build();
        append(meta, id, event);
    }

    private EventMeta meta(String eventType, String notificationId) {
        return EventMeta.newEvent(
                clock,
                eventType,
                SCHEMA_VERSION,
                AGGREGATE,
                notificationId,
                notificationId,
                null,
                null,
                PRODUCER);
    }

    private void append(EventMeta meta, String key, SpecificRecord event) {
        byte[] payload = serializer.serialize(TOPIC, event);
        OutboxRecord row =
                new OutboxRecord(
                        Uuid7.generate(clock),
                        AGGREGATE,
                        key,
                        meta.eventType(),
                        meta.eventId(),
                        TOPIC,
                        key.getBytes(StandardCharsets.UTF_8),
                        payload,
                        EventHeaders.toHeaders(meta),
                        0,
                        null,
                        clock.instant(),
                        null);
        outbox.append(row);
    }
}
