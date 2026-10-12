package com.lms.origination.adapters.in.kafka;

import com.lms.contracts.decision.DecisionCompleted;
import com.lms.contracts.events.MoneyAmount;
import com.lms.money.Money;
import com.lms.origination.application.OriginationService;
import com.lms.origination.application.OriginationService.DecisionOutcome;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.Currency;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code decision.completed} and drives the application to OFFERED/REJECTED (or leaves it
 * decisioned for REFER). Idempotent via the inbox, in the handler's transaction.
 */
@Component
public class DecisionCompletedListener {

    static final String TOPIC = "decision.events";

    private final OriginationService origination;
    private final IdempotentConsumer idempotent;

    public DecisionCompletedListener(
            OriginationService origination, IdempotentConsumer idempotent) {
        this.origination = origination;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "origination")
    @Transactional
    public void onMessage(Object payload) {
        if (!(payload instanceof DecisionCompleted event)) {
            return;
        }
        UUID eventId = UUID.fromString(event.getMetadata().getEventId());
        idempotent.process(
                eventId,
                TOPIC,
                () ->
                        origination.onDecision(
                                event.getApplicationId(),
                                DecisionOutcome.valueOf(event.getOutcome()),
                                limit(event.getOfferedLimit())));
    }

    private static Money limit(MoneyAmount amount) {
        return amount == null
                ? null
                : Money.of(amount.getMinorUnits(), Currency.getInstance(amount.getCurrency()));
    }
}
