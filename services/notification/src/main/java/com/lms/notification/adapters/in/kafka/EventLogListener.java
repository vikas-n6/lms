package com.lms.notification.adapters.in.kafka;

import com.lms.contracts.events.EventMetadata;
import com.lms.notification.application.NotificationSink;
import com.lms.notification.domain.NotificationFormatter;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 1 stub: consume {@code customer.events} and {@code application.events} and deliver a
 * non-PII notification via the SMS simulator. Idempotent via the inbox (CLAUDE.md "no consumer
 * without an inbox"). The envelope is read generically from each event's {@code metadata} field.
 */
@Component
public class EventLogListener {

    private final NotificationSink sink;
    private final IdempotentConsumer idempotent;

    public EventLogListener(NotificationSink sink, IdempotentConsumer idempotent) {
        this.sink = sink;
        this.idempotent = idempotent;
    }

    @KafkaListener(
            topics = {"customer.events", "application.events"},
            groupId = "notification")
    @Transactional
    public void onMessage(Object payload) {
        if (!(payload instanceof SpecificRecord record)) {
            return;
        }
        var field = record.getSchema().getField("metadata");
        if (field == null || !(record.get(field.pos()) instanceof EventMetadata meta)) {
            return;
        }
        UUID eventId = UUID.fromString(meta.getEventId());
        idempotent.process(
                eventId,
                meta.getAggregateType() + ".events",
                () ->
                        sink.deliver(
                                NotificationFormatter.line(
                                        meta.getEventType(),
                                        meta.getAggregateType(),
                                        meta.getAggregateId())));
    }
}
