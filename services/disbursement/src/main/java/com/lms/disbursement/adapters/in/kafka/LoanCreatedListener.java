package com.lms.disbursement.adapters.in.kafka;

import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.loan.LoanCreated;
import com.lms.disbursement.application.RegisterDisbursementService;
import com.lms.disbursement.application.RegisterDisbursementService.NewDisbursement;
import com.lms.money.Money;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.Currency;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code loan.created} from {@code loan.events} and starts the payout saga exactly once
 * (inbox idempotency). A duplicate delivery is skipped; the workflow id {@code disb-{loanId}} is
 * the second guard against a double start.
 */
@Component
public class LoanCreatedListener {

    static final String TOPIC = "loan.events";

    private final RegisterDisbursementService register;
    private final IdempotentConsumer idempotent;

    public LoanCreatedListener(
            RegisterDisbursementService register, IdempotentConsumer idempotent) {
        this.register = register;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "disbursement")
    @Transactional
    public void onMessage(Object payload) {
        if (!(payload instanceof LoanCreated event)) {
            return;
        }
        UUID eventId = UUID.fromString(event.getMetadata().getEventId());
        idempotent.process(
                eventId,
                TOPIC,
                () ->
                        register.register(
                                new NewDisbursement(
                                        UUID.fromString(event.getLoanId()),
                                        event.getLoanNumber(),
                                        money(event.getPrincipal()),
                                        money(event.getFee()),
                                        money(event.getGst()),
                                        money(event.getNetDisbursal()))));
    }

    private static Money money(MoneyAmount amount) {
        return Money.of(amount.getMinorUnits(), Currency.getInstance(amount.getCurrency()));
    }
}
