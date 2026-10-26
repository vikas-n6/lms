package com.lms.collections.adapters.in.kafka;

import com.lms.collections.application.CollectionsService;
import com.lms.contracts.loan.LoanClosed;
import com.lms.contracts.loan.LoanDpdUpdated;
import com.lms.contracts.loan.LoanRepaymentAllocated;
import com.lms.contracts.loan.LoanWrittenOff;
import com.lms.outbox.inbox.IdempotentConsumer;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code loan.events} that drive collection cases (phase prompt task 4): DPD ticks open
 * and advance cases, a repayment allocation is a payment signal, a close resolves the case, and a
 * write-off closes it. Idempotent via the inbox (CLAUDE.md "no consumer without an inbox").
 */
@Component
public class LoanEventsListener {

    static final String TOPIC = "loan.events";

    private final CollectionsService service;
    private final IdempotentConsumer idempotent;

    public LoanEventsListener(CollectionsService service, IdempotentConsumer idempotent) {
        this.service = service;
        this.idempotent = idempotent;
    }

    @KafkaListener(topics = TOPIC, groupId = "collections")
    @Transactional
    public void onMessage(Object payload) {
        if (payload instanceof LoanDpdUpdated event) {
            process(
                    event.getMetadata().getEventId(),
                    () ->
                            service.onDpdUpdated(
                                    event.getLoanId(),
                                    event.getLoanNumber(),
                                    event.getDaysPastDue(),
                                    event.getBucket(),
                                    event.getBusinessDate()));
        } else if (payload instanceof LoanRepaymentAllocated event) {
            process(
                    event.getMetadata().getEventId(),
                    () -> service.onRepaymentAllocated(event.getLoanId()));
        } else if (payload instanceof LoanClosed event) {
            process(
                    event.getMetadata().getEventId(),
                    () -> service.onLoanClosed(event.getLoanId()));
        } else if (payload instanceof LoanWrittenOff event) {
            process(
                    event.getMetadata().getEventId(),
                    () -> service.onLoanWrittenOff(event.getLoanId()));
        }
    }

    private void process(String eventIdRaw, Runnable handler) {
        idempotent.process(UUID.fromString(eventIdRaw), TOPIC, handler);
    }
}
