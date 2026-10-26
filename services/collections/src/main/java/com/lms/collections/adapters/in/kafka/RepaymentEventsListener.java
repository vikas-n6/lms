package com.lms.collections.adapters.in.kafka;

import com.lms.collections.application.CollectionsService;
import com.lms.contracts.repayment.MandateDebitFailed;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code repayment.events} relevant to collections (phase prompt task 4): a failed mandate
 * auto-debit. Idempotent via the inbox. The DPD-7 re-presentation step already handles the retry,
 * so this is informational for the case timeline.
 */
@Component
public class RepaymentEventsListener {

    static final String TOPIC = "repayment.events";

    private final CollectionsService service;
    private final IdempotentConsumer idempotent;

    public RepaymentEventsListener(CollectionsService service, IdempotentConsumer idempotent) {
        this.service = service;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "collections")
    @Transactional
    public void onMessage(Object payload) {
        if (payload instanceof MandateDebitFailed event) {
            idempotent.process(
                    UUID.fromString(event.getMetadata().getEventId()),
                    TOPIC,
                    () -> service.onMandateDebitFailed(event.getLoanId(), event.getFailureCode()));
        }
    }
}
