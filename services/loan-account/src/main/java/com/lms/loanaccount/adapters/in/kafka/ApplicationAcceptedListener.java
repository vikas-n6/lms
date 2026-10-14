package com.lms.loanaccount.adapters.in.kafka;

import com.lms.contracts.application.ApplicationAccepted;
import com.lms.contracts.events.MoneyAmount;
import com.lms.loanaccount.application.AcceptedApplication;
import com.lms.loanaccount.application.CreateLoanService;
import com.lms.money.Money;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.Currency;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code application.accepted} from {@code application.events} and creates the loan
 * exactly once (inbox idempotency, in the handler's transaction so offsets commit only after it
 * commits — docs/architecture.md). Other event types on the topic are ignored.
 */
@Component
public class ApplicationAcceptedListener {

    static final String TOPIC = "application.events";

    private final CreateLoanService createLoanService;
    private final IdempotentConsumer idempotent;

    public ApplicationAcceptedListener(
            CreateLoanService createLoanService, IdempotentConsumer idempotent) {
        this.createLoanService = createLoanService;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "loan-account")
    @Transactional
    public void onMessage(Object payload) {
        if (!(payload instanceof ApplicationAccepted event)) {
            return;
        }
        UUID eventId = UUID.fromString(event.getMetadata().getEventId());
        idempotent.process(
                eventId, TOPIC, () -> createLoanService.create(toCommand(event), eventId));
    }

    private static AcceptedApplication toCommand(ApplicationAccepted event) {
        return new AcceptedApplication(
                event.getApplicationId(),
                event.getCustomerId(),
                event.getProductId(),
                money(event.getPrincipal()),
                event.getTenorMonths(),
                money(event.getEmi()),
                money(event.getFee()),
                money(event.getGst()),
                money(event.getNetDisbursal()));
    }

    private static Money money(MoneyAmount amount) {
        return Money.of(amount.getMinorUnits(), Currency.getInstance(amount.getCurrency()));
    }
}
