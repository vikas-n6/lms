package com.lms.loanaccount.adapters.in.kafka;

import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.repayment.RepaymentReceived;
import com.lms.contracts.repayment.RepaymentReversed;
import com.lms.loanaccount.application.AllocateRepaymentService;
import com.lms.loanaccount.application.ForecloseLoanService;
import com.lms.loanaccount.application.ReverseAllocationService;
import com.lms.money.Money;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.Currency;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code repayment.events}: {@code repayment.received} runs the allocation waterfall
 * (Phase 3 produces it), or — when {@code purpose=FORECLOSURE} — the foreclosure close; {@code
 * repayment.reversed} undoes a prior allocation. Each runs exactly once (inbox idempotency) inside
 * the handler's transaction.
 */
@Component
public class RepaymentEventsListener {

    static final String TOPIC = "repayment.events";
    private static final String FORECLOSURE = "FORECLOSURE";

    private final AllocateRepaymentService allocateService;
    private final ForecloseLoanService forecloseService;
    private final ReverseAllocationService reverseService;
    private final IdempotentConsumer idempotent;

    public RepaymentEventsListener(
            AllocateRepaymentService allocateService,
            ForecloseLoanService forecloseService,
            ReverseAllocationService reverseService,
            IdempotentConsumer idempotent) {
        this.allocateService = allocateService;
        this.forecloseService = forecloseService;
        this.reverseService = reverseService;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "loan-account")
    @Transactional
    public void onMessage(Object payload) {
        if (payload instanceof RepaymentReceived event) {
            UUID eventId = UUID.fromString(event.getMetadata().getEventId());
            UUID loanId = UUID.fromString(event.getLoanId());
            Money amount = money(event.getAmount());
            idempotent.process(
                    eventId,
                    TOPIC,
                    () -> {
                        if (isForeclosure(event)) {
                            forecloseService.foreclose(
                                    loanId, event.getRepaymentId(), amount, eventId);
                        } else {
                            allocateService.allocate(
                                    loanId, event.getRepaymentId(), amount, eventId);
                        }
                    });
        } else if (payload instanceof RepaymentReversed event) {
            UUID eventId = UUID.fromString(event.getMetadata().getEventId());
            idempotent.process(
                    eventId,
                    TOPIC,
                    () ->
                            reverseService.reverse(
                                    UUID.fromString(event.getLoanId()),
                                    event.getRepaymentId(),
                                    eventId));
        }
    }

    private static boolean isForeclosure(RepaymentReceived event) {
        return event.getPurpose() != null && FORECLOSURE.contentEquals(event.getPurpose());
    }

    private static Money money(MoneyAmount amount) {
        return Money.of(amount.getMinorUnits(), Currency.getInstance(amount.getCurrency()));
    }
}
