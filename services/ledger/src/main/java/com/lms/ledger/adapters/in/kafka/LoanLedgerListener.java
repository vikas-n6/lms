package com.lms.ledger.adapters.in.kafka;

import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.loan.LoanInterestAccrued;
import com.lms.ledger.application.PostEntryService;
import com.lms.ledger.application.PostingRequest;
import com.lms.money.Money;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts ledger entries for loan events. In Phase 2 only {@code loan.interest-accrued} posts (DR
 * Interest receivable / CR Interest income); repayment/penal/write-off templates arrive in Phases
 * 3–4. Idempotent via the inbox and the unique {@code source_event_id}.
 */
@Component
public class LoanLedgerListener {

    static final String TOPIC = "loan.events";

    private final PostEntryService postService;
    private final IdempotentConsumer idempotent;

    public LoanLedgerListener(PostEntryService postService, IdempotentConsumer idempotent) {
        this.postService = postService;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "ledger")
    @Transactional
    public void onMessage(Object payload) {
        if (!(payload instanceof LoanInterestAccrued event)) {
            return; // other loan events do not post in Phase 2
        }
        UUID eventId = UUID.fromString(event.getMetadata().getEventId());
        idempotent.process(
                eventId,
                TOPIC,
                () ->
                        postService.post(
                                new PostingRequest(
                                        "loan.interest-accrued",
                                        eventId,
                                        event.getLoanId(),
                                        null,
                                        currency(event.getAmount()),
                                        Map.of("interest", money(event.getAmount())))));
    }

    private static Money money(MoneyAmount amount) {
        return Money.of(amount.getMinorUnits(), Currency.getInstance(amount.getCurrency()));
    }

    private static Currency currency(MoneyAmount amount) {
        return Currency.getInstance(amount.getCurrency());
    }
}
