package com.lms.reporting.adapters.in.kafka;

import com.lms.contracts.ledger.LedgerReconCompleted;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.reporting.application.ReconStatusProjection;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Projects ledger.recon-completed onto the reconciliation-status read model (ADR 0013). */
@Component
public class LedgerEventsListener {

    static final String TOPIC = "ledger.events";

    private final ReconStatusProjection projection;
    private final IdempotentConsumer idempotent;

    public LedgerEventsListener(ReconStatusProjection projection, IdempotentConsumer idempotent) {
        this.projection = projection;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "reporting")
    @Transactional
    public void onMessage(Object payload) {
        if (payload instanceof LedgerReconCompleted e) {
            idempotent.process(
                    UUID.fromString(e.getMetadata().getEventId()),
                    TOPIC,
                    () -> projection.onReconCompleted(e));
        }
        // ledger.entry-posted / entry-reversed are not projected into a read model here.
    }
}
