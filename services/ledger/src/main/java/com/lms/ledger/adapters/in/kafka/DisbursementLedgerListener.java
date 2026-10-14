package com.lms.ledger.adapters.in.kafka;

import com.lms.contracts.disbursement.DisbursementFailed;
import com.lms.contracts.disbursement.DisbursementInitiated;
import com.lms.contracts.disbursement.DisbursementSucceeded;
import com.lms.contracts.events.MoneyAmount;
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
 * Posts ledger entries for disbursement events (docs/domain-rules.md "Posting templates").
 * Idempotent via the inbox and the unique {@code source_event_id}.
 */
@Component
public class DisbursementLedgerListener {

    static final String TOPIC = "disbursement.events";

    private final PostEntryService postService;
    private final IdempotentConsumer idempotent;

    public DisbursementLedgerListener(PostEntryService postService, IdempotentConsumer idempotent) {
        this.postService = postService;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "ledger")
    @Transactional
    public void onMessage(Object payload) {
        if (payload instanceof DisbursementInitiated e) {
            UUID eventId = UUID.fromString(e.getMetadata().getEventId());
            idempotent.process(
                    eventId,
                    TOPIC,
                    () ->
                            postService.post(
                                    new PostingRequest(
                                            "disbursement.initiated",
                                            eventId,
                                            e.getLoanId(),
                                            null,
                                            currency(e.getNetAmount()),
                                            Map.of("net", money(e.getNetAmount())))));
        } else if (payload instanceof DisbursementSucceeded e) {
            UUID eventId = UUID.fromString(e.getMetadata().getEventId());
            idempotent.process(
                    eventId,
                    TOPIC,
                    () ->
                            postService.post(
                                    new PostingRequest(
                                            "disbursement.succeeded",
                                            eventId,
                                            e.getLoanId(),
                                            null,
                                            currency(e.getPrincipal()),
                                            Map.of(
                                                    "principal", money(e.getPrincipal()),
                                                    "net", money(e.getNetAmount()),
                                                    "fee", money(e.getFee()),
                                                    "gst", money(e.getGst())))));
        } else if (payload instanceof DisbursementFailed e) {
            UUID eventId = UUID.fromString(e.getMetadata().getEventId());
            idempotent.process(
                    eventId,
                    TOPIC,
                    () ->
                            postService.post(
                                    new PostingRequest(
                                            "disbursement.failed",
                                            eventId,
                                            e.getLoanId(),
                                            null,
                                            currency(e.getNetAmount()),
                                            Map.of("net", money(e.getNetAmount())))));
        }
    }

    private static Money money(MoneyAmount amount) {
        return Money.of(amount.getMinorUnits(), Currency.getInstance(amount.getCurrency()));
    }

    private static Currency currency(MoneyAmount amount) {
        return Currency.getInstance(amount.getCurrency());
    }
}
