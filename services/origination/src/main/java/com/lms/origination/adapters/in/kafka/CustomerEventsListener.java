package com.lms.origination.adapters.in.kafka;

import com.lms.contracts.customer.CustomerBankAccountLinked;
import com.lms.contracts.customer.CustomerKycCompleted;
import com.lms.origination.application.port.out.CustomerProjectionUpdater;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the customer KYC/bank projection from {@code customer.events}, so submission can be
 * gated without calling the customer service over REST. Idempotent via the inbox.
 */
@Component
public class CustomerEventsListener {

    static final String TOPIC = "customer.events";

    private final CustomerProjectionUpdater projection;
    private final IdempotentConsumer idempotent;

    public CustomerEventsListener(
            CustomerProjectionUpdater projection, IdempotentConsumer idempotent) {
        this.projection = projection;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "origination")
    @Transactional
    public void onMessage(Object payload) {
        switch (payload) {
            case CustomerKycCompleted e -> {
                if ("VERIFIED".equals(e.getStatus())) {
                    idempotent.process(
                            eventId(e.getMetadata().getEventId()),
                            TOPIC,
                            () -> projection.markKycVerified(e.getCustomerId()));
                }
            }
            case CustomerBankAccountLinked e -> {
                if (e.getPennyDropVerified()) {
                    idempotent.process(
                            eventId(e.getMetadata().getEventId()),
                            TOPIC,
                            () -> projection.markBankLinked(e.getCustomerId()));
                }
            }
            default -> {
                /* ignore other customer events */
            }
        }
    }

    private static UUID eventId(String id) {
        return UUID.fromString(id);
    }
}
