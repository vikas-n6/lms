package com.lms.reporting.adapters.in.kafka;

import com.lms.contracts.loan.LoanActivated;
import com.lms.contracts.loan.LoanCancelled;
import com.lms.contracts.loan.LoanClosed;
import com.lms.contracts.loan.LoanCreated;
import com.lms.contracts.loan.LoanDpdUpdated;
import com.lms.contracts.loan.LoanForeclosed;
import com.lms.contracts.loan.LoanInstallmentDueSoon;
import com.lms.contracts.loan.LoanInterestAccrued;
import com.lms.contracts.loan.LoanPenalAccrued;
import com.lms.contracts.loan.LoanRepaymentAllocated;
import com.lms.contracts.loan.LoanRepaymentReversed;
import com.lms.contracts.loan.LoanWrittenOff;
import com.lms.outbox.inbox.IdempotentConsumer;
import com.lms.reporting.application.LoanProjection;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Projects loan.events onto the loan read model and collection counters (ADR 0013). */
@Component
public class LoanEventsListener {

    static final String TOPIC = "loan.events";

    private final LoanProjection projection;
    private final IdempotentConsumer idempotent;

    public LoanEventsListener(LoanProjection projection, IdempotentConsumer idempotent) {
        this.projection = projection;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "reporting")
    @Transactional
    public void onMessage(Object payload) {
        switch (payload) {
            case LoanCreated e -> run(e.getMetadata().getEventId(), () -> projection.onCreated(e));
            case LoanActivated e ->
                    run(e.getMetadata().getEventId(), () -> projection.onActivated(e));
            case LoanInterestAccrued e ->
                    run(e.getMetadata().getEventId(), () -> projection.onInterestAccrued(e));
            case LoanPenalAccrued e ->
                    run(e.getMetadata().getEventId(), () -> projection.onPenalAccrued(e));
            case LoanInstallmentDueSoon e ->
                    run(e.getMetadata().getEventId(), () -> projection.onInstallmentDueSoon(e));
            case LoanRepaymentAllocated e ->
                    run(e.getMetadata().getEventId(), () -> projection.onRepaymentAllocated(e));
            case LoanRepaymentReversed e ->
                    run(e.getMetadata().getEventId(), () -> projection.onRepaymentReversed(e));
            case LoanDpdUpdated e ->
                    run(e.getMetadata().getEventId(), () -> projection.onDpdUpdated(e));
            case LoanClosed e -> run(e.getMetadata().getEventId(), () -> projection.onClosed(e));
            case LoanForeclosed e ->
                    run(e.getMetadata().getEventId(), () -> projection.onForeclosed(e));
            case LoanWrittenOff e ->
                    run(e.getMetadata().getEventId(), () -> projection.onWrittenOff(e));
            case LoanCancelled e ->
                    run(e.getMetadata().getEventId(), () -> projection.onCancelled(e));
            default -> {
                // Other loan.events carry no read-model impact; ignore.
            }
        }
    }

    private void run(String eventId, Runnable handler) {
        idempotent.process(UUID.fromString(eventId), TOPIC, handler);
    }
}
