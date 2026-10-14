package com.lms.loanaccount.adapters.in.kafka;

import com.lms.contracts.disbursement.DisbursementFailed;
import com.lms.contracts.disbursement.DisbursementSucceeded;
import com.lms.contracts.events.MoneyAmount;
import com.lms.loanaccount.application.ActivateLoanService;
import com.lms.loanaccount.application.CancelLoanService;
import com.lms.money.Money;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.Currency;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code disbursement.succeeded} / {@code disbursement.failed} from {@code
 * disbursement.events} (Phase 3 produces them). Succeeded activates the loan and generates the real
 * schedule; failed cancels it. Idempotent via the inbox.
 */
@Component
public class DisbursementEventsListener {

    static final String TOPIC = "disbursement.events";

    private final ActivateLoanService activateService;
    private final CancelLoanService cancelService;
    private final IdempotentConsumer idempotent;

    public DisbursementEventsListener(
            ActivateLoanService activateService,
            CancelLoanService cancelService,
            IdempotentConsumer idempotent) {
        this.activateService = activateService;
        this.cancelService = cancelService;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "loan-account")
    @Transactional
    public void onMessage(Object payload) {
        if (payload instanceof DisbursementSucceeded event) {
            UUID eventId = UUID.fromString(event.getMetadata().getEventId());
            idempotent.process(
                    eventId,
                    TOPIC,
                    () ->
                            activateService.activate(
                                    UUID.fromString(event.getLoanId()),
                                    money(event.getNetAmount()),
                                    event.getDisbursementDate(),
                                    eventId));
        } else if (payload instanceof DisbursementFailed event) {
            UUID eventId = UUID.fromString(event.getMetadata().getEventId());
            idempotent.process(
                    eventId,
                    TOPIC,
                    () ->
                            cancelService.cancel(
                                    UUID.fromString(event.getLoanId()),
                                    event.getReason(),
                                    eventId));
        }
    }

    private static Money money(MoneyAmount amount) {
        return Money.of(amount.getMinorUnits(), Currency.getInstance(amount.getCurrency()));
    }
}
