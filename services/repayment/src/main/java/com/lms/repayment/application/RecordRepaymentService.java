package com.lms.repayment.application;

import com.lms.events.Uuid7;
import com.lms.money.Money;
import com.lms.repayment.application.port.out.CallbackDedupStore;
import com.lms.repayment.application.port.out.RepaymentEventPublisher;
import com.lms.repayment.application.port.out.RepaymentRepository;
import com.lms.repayment.domain.Repayment;
import com.lms.repayment.domain.RepaymentChannel;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Records confirmed funds as a repayment and publishes {@code repayment.received} (phase prompt
 * task 3). Idempotent on the provider reference + {@code RECEIVED}: a duplicate callback produces
 * exactly one repayment (and so exactly one allocation downstream).
 */
public class RecordRepaymentService {

    private final RepaymentRepository repayments;
    private final RepaymentEventPublisher publisher;
    private final CallbackDedupStore dedup;
    private final Clock clock;

    public RecordRepaymentService(
            RepaymentRepository repayments,
            RepaymentEventPublisher publisher,
            CallbackDedupStore dedup,
            Clock clock) {
        this.repayments = Objects.requireNonNull(repayments);
        this.publisher = Objects.requireNonNull(publisher);
        this.dedup = Objects.requireNonNull(dedup);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Record a confirmed repayment. Returns empty when the callback is a duplicate. */
    public Optional<Repayment> record(
            UUID loanId,
            Money amount,
            RepaymentChannel channel,
            String purpose,
            String foreclosureQuoteId,
            String providerReference) {
        if (!dedup.firstSeen(providerReference, "RECEIVED")) {
            return Optional.empty();
        }
        Repayment repayment =
                Repayment.received(
                        Uuid7.generate(clock),
                        loanId,
                        amount,
                        channel,
                        purpose,
                        foreclosureQuoteId,
                        providerReference,
                        clock.instant());
        repayments.save(repayment);
        publisher.repaymentReceived(repayment);
        return Optional.of(repayment);
    }
}
