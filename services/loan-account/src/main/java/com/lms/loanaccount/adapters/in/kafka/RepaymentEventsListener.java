package com.lms.loanaccount.adapters.in.kafka;

import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.repayment.RepaymentReceived;
import com.lms.loanaccount.application.AllocateRepaymentService;
import com.lms.money.Money;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.Currency;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code repayment.received} from {@code repayment.events} (Phase 3 produces it) and runs
 * the allocation waterfall exactly once (inbox idempotency, in the handler's transaction).
 */
@Component
public class RepaymentEventsListener {

    static final String TOPIC = "repayment.events";

    private final AllocateRepaymentService allocateService;
    private final IdempotentConsumer idempotent;

    public RepaymentEventsListener(
            AllocateRepaymentService allocateService, IdempotentConsumer idempotent) {
        this.allocateService = allocateService;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "loan-account")
    @Transactional
    public void onMessage(Object payload) {
        if (!(payload instanceof RepaymentReceived event)) {
            return;
        }
        UUID eventId = UUID.fromString(event.getMetadata().getEventId());
        idempotent.process(
                eventId,
                TOPIC,
                () ->
                        allocateService.allocate(
                                UUID.fromString(event.getLoanId()),
                                event.getRepaymentId(),
                                money(event.getAmount()),
                                eventId));
    }

    private static Money money(MoneyAmount amount) {
        return Money.of(amount.getMinorUnits(), Currency.getInstance(amount.getCurrency()));
    }
}
