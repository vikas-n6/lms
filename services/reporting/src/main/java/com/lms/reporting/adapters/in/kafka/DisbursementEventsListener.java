package com.lms.reporting.adapters.in.kafka;

import com.lms.contracts.disbursement.DisbursementSucceeded;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.reporting.application.LoanProjection;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Projects disbursement.events: a success sets the loan's disbursed amount and disbursal month. */
@Component
public class DisbursementEventsListener {

    static final String TOPIC = "disbursement.events";

    private final LoanProjection projection;
    private final IdempotentConsumer idempotent;

    public DisbursementEventsListener(LoanProjection projection, IdempotentConsumer idempotent) {
        this.projection = projection;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "reporting")
    @Transactional
    public void onMessage(Object payload) {
        if (payload instanceof DisbursementSucceeded e) {
            idempotent.process(
                    UUID.fromString(e.getMetadata().getEventId()),
                    TOPIC,
                    () -> projection.onDisbursementSucceeded(e));
        }
    }
}
