package com.lms.ledger.adapters.in.kafka;

import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.repayment.RepaymentReceived;
import com.lms.contracts.repayment.RepaymentReversed;
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
 * Posts ledger entries for repayment events (Phase 3): {@code repayment.received} (DR Bank / CR
 * Customer suspense) and {@code repayment.reversed} (the mirror, for a returned debit). The
 * allocation split across the component receivables is driven separately by {@code
 * loan.repayment-allocated}. Idempotent via the inbox and the unique {@code source_event_id}.
 */
@Component
public class RepaymentLedgerListener {

    static final String TOPIC = "repayment.events";

    private final PostEntryService postService;
    private final IdempotentConsumer idempotent;

    public RepaymentLedgerListener(PostEntryService postService, IdempotentConsumer idempotent) {
        this.postService = postService;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "ledger")
    @Transactional
    public void onMessage(Object payload) {
        if (payload instanceof RepaymentReceived event) {
            // The received money lands on the operating account with the rail reference, so the
            // entry is tagged for the bank/rail reconciliation (phase prompt task 6).
            post(
                    event.getMetadata().getEventId(),
                    "repayment.received",
                    event.getLoanId(),
                    event.getAmount(),
                    event.getRailReference());
        } else if (payload instanceof RepaymentReversed event) {
            post(
                    event.getMetadata().getEventId(),
                    "repayment.reversed",
                    event.getLoanId(),
                    event.getAmount(),
                    null);
        }
    }

    private void post(
            String eventIdRaw,
            String eventType,
            String loanId,
            MoneyAmount amount,
            String externalReference) {
        UUID eventId = UUID.fromString(eventIdRaw);
        Currency currency = Currency.getInstance(amount.getCurrency());
        Money money = Money.of(amount.getMinorUnits(), currency);
        PostingRequest request =
                new PostingRequest(
                        eventType, eventId, loanId, null, currency, Map.of("amount", money));
        if (externalReference != null) {
            request = request.withExternal(externalReference, money);
        }
        PostingRequest toPost = request;
        idempotent.process(eventId, TOPIC, () -> postService.post(toPost));
    }
}
