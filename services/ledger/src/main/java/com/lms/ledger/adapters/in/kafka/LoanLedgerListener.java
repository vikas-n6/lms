package com.lms.ledger.adapters.in.kafka;

import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.loan.LoanInterestAccrued;
import com.lms.contracts.loan.LoanPenalAccrued;
import com.lms.contracts.loan.LoanRepaymentAllocated;
import com.lms.contracts.loan.LoanRepaymentReversed;
import com.lms.contracts.loan.LoanWrittenOff;
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
 * Posts ledger entries for loan events: {@code loan.interest-accrued} (DR Interest receivable / CR
 * Interest income), and in Phase 3 {@code loan.repayment-allocated} (DR Customer suspense / CR the
 * component receivables) and {@code loan.repayment-reversed} (the mirror). Idempotent via the inbox
 * and the unique {@code source_event_id}. A zero-total allocation posts nothing (nothing left
 * suspense).
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
        if (payload instanceof LoanInterestAccrued event) {
            post(
                    event.getMetadata().getEventId(),
                    "loan.interest-accrued",
                    event.getLoanId(),
                    currency(event.getAmount()),
                    Map.of("interest", money(event.getAmount())));
        } else if (payload instanceof LoanRepaymentAllocated event) {
            if (event.getTotalAllocated().getMinorUnits() == 0L) {
                return; // everything held as advance; nothing leaves suspense
            }
            post(
                    event.getMetadata().getEventId(),
                    "loan.repayment-allocated",
                    event.getLoanId(),
                    currency(event.getTotalAllocated()),
                    Map.of(
                            "total", money(event.getTotalAllocated()),
                            "penal", money(event.getPenal()),
                            "interest", money(event.getInterest()),
                            "principal", money(event.getPrincipal())));
        } else if (payload instanceof LoanRepaymentReversed event) {
            post(
                    event.getMetadata().getEventId(),
                    "loan.repayment-reversed",
                    event.getLoanId(),
                    currency(event.getTotalReversed()),
                    Map.of(
                            "total", money(event.getTotalReversed()),
                            "penal", money(event.getPenal()),
                            "interest", money(event.getInterest()),
                            "principal", money(event.getPrincipal())));
        } else if (payload instanceof LoanPenalAccrued event) {
            post(
                    event.getMetadata().getEventId(),
                    "loan.penal-accrued",
                    event.getLoanId(),
                    currency(event.getAmount()),
                    Map.of("penal", money(event.getAmount())));
        } else if (payload instanceof LoanWrittenOff event) {
            post(
                    event.getMetadata().getEventId(),
                    "loan.written-off",
                    event.getLoanId(),
                    currency(event.getTotal()),
                    Map.of(
                            "principal", money(event.getPrincipal()),
                            "interest", money(event.getInterest()),
                            "penal", money(event.getPenal())));
        }
    }

    private void post(
            String eventIdRaw,
            String eventType,
            String loanId,
            Currency currency,
            Map<String, Money> amounts) {
        UUID eventId = UUID.fromString(eventIdRaw);
        idempotent.process(
                eventId,
                TOPIC,
                () ->
                        postService.post(
                                new PostingRequest(
                                        eventType, eventId, loanId, null, currency, amounts)));
    }

    private static Money money(MoneyAmount amount) {
        return Money.of(amount.getMinorUnits(), Currency.getInstance(amount.getCurrency()));
    }

    private static Currency currency(MoneyAmount amount) {
        return Currency.getInstance(amount.getCurrency());
    }
}
