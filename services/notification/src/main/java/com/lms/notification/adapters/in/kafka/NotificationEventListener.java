package com.lms.notification.adapters.in.kafka;

import com.lms.contracts.collections.CollectionsReminderDue;
import com.lms.contracts.events.EventMetadata;
import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.loan.LoanActivated;
import com.lms.contracts.loan.LoanClosed;
import com.lms.contracts.loan.LoanInstallmentDueSoon;
import com.lms.contracts.repayment.MandateDebitFailed;
import com.lms.contracts.repayment.RepaymentReceived;
import com.lms.contracts.repayment.RepaymentReversed;
import com.lms.notification.application.NotificationService;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscribes to the trigger events (phase prompt task 5) and fans each out to the configured
 * templates/channels via {@link NotificationService}. The routing key is the event's {@code
 * eventType} and the dedup key its {@code eventId}; placeholders are built from non-PII fields only
 * (CLAUDE.md "Logging"). Idempotent via the inbox; the service also dedups on (event, template,
 * channel).
 */
@Component
public class NotificationEventListener {

    private final NotificationService service;
    private final IdempotentConsumer idempotent;

    public NotificationEventListener(NotificationService service, IdempotentConsumer idempotent) {
        this.service = service;
        this.idempotent = idempotent;
    }

    @KafkaListener(
            topics = {
                "application.events",
                "disbursement.events",
                "loan.events",
                "repayment.events",
                "collections.events"
            },
            groupId = "notification")
    @Transactional
    public void onMessage(Object payload) {
        if (!(payload instanceof SpecificRecord record)) {
            return;
        }
        var field = record.getSchema().getField("metadata");
        if (field == null || !(record.get(field.pos()) instanceof EventMetadata meta)) {
            return;
        }
        Map<String, String> placeholders = placeholders(payload);
        String recipient = meta.getAggregateId();
        idempotent.process(
                UUID.fromString(meta.getEventId()),
                meta.getAggregateType() + ".events",
                () ->
                        service.notify(
                                meta.getEventId(), meta.getEventType(), recipient, placeholders));
    }

    private static Map<String, String> placeholders(Object payload) {
        Map<String, String> p = new HashMap<>();
        switch (payload) {
            case LoanActivated e -> {
                p.put("loanNumber", e.getLoanNumber());
                p.put("firstDueDate", String.valueOf(e.getFirstDueDate()));
            }
            case LoanInstallmentDueSoon e -> {
                p.put("loanNumber", e.getLoanNumber());
                p.put("instalmentNumber", String.valueOf(e.getInstalmentNumber()));
                p.put("dueDate", String.valueOf(e.getDueDate()));
                p.put("amount", money(e.getAmountDue()));
            }
            case RepaymentReceived e -> p.put("amount", money(e.getAmount()));
            case RepaymentReversed e -> {
                p.put("amount", money(e.getAmount()));
                p.put("reason", e.getReason());
            }
            case MandateDebitFailed e -> {
                p.put("failureCode", e.getFailureCode());
                p.put("dueDate", String.valueOf(e.getDueDate()));
            }
            case CollectionsReminderDue e -> {
                p.put("loanNumber", e.getLoanNumber());
                p.put("stepId", e.getStepId());
            }
            case LoanClosed e -> {
                p.put("loanNumber", e.getLoanNumber());
                p.put("reason", e.getReason());
            }
            default -> {
                // application.offered, disbursement.failed: routed with metadata only.
            }
        }
        return p;
    }

    private static String money(MoneyAmount amount) {
        return amount.getMinorUnits() + " " + amount.getCurrency();
    }
}
