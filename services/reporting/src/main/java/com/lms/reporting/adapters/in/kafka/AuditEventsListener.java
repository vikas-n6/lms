package com.lms.reporting.adapters.in.kafka;

import com.lms.contracts.audit.AuditEvent;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.reporting.application.AuditProjection;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores audit.events into the append-only audit log (ADR 0014). */
@Component
public class AuditEventsListener {

    static final String TOPIC = "audit.events";

    private final AuditProjection projection;
    private final IdempotentConsumer idempotent;

    public AuditEventsListener(AuditProjection projection, IdempotentConsumer idempotent) {
        this.projection = projection;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "reporting")
    @Transactional
    public void onMessage(Object payload) {
        if (payload instanceof AuditEvent e) {
            idempotent.process(
                    UUID.fromString(e.getMetadata().getEventId()),
                    TOPIC,
                    () -> projection.onAuditEvent(e));
        }
    }
}
