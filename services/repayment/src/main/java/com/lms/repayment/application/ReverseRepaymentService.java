package com.lms.repayment.application;

import com.lms.repayment.application.port.out.CallbackDedupStore;
import com.lms.repayment.application.port.out.RepaymentEventPublisher;
import com.lms.repayment.application.port.out.RepaymentRepository;
import com.lms.repayment.domain.Repayment;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Reverses a previously confirmed repayment (a post-success return) and publishes {@code
 * repayment.reversed} (phase prompt task 3). Idempotent on the provider reference + {@code
 * REVERSED}; a return for an unknown/unreceived reference is a no-op.
 */
public class ReverseRepaymentService {

    private final RepaymentRepository repayments;
    private final RepaymentEventPublisher publisher;
    private final CallbackDedupStore dedup;
    private final Clock clock;

    public ReverseRepaymentService(
            RepaymentRepository repayments,
            RepaymentEventPublisher publisher,
            CallbackDedupStore dedup,
            Clock clock) {
        this.repayments = Objects.requireNonNull(repayments);
        this.publisher = Objects.requireNonNull(publisher);
        this.dedup = Objects.requireNonNull(dedup);
        this.clock = Objects.requireNonNull(clock);
    }

    public Optional<Repayment> reverse(String providerReference, String reason) {
        if (!dedup.firstSeen(providerReference, "REVERSED")) {
            return Optional.empty();
        }
        Optional<Repayment> existing = repayments.findByProviderReference(providerReference);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Repayment reversed = existing.get().reversed(clock.instant());
        repayments.save(reversed);
        publisher.repaymentReversed(reversed, reason);
        return Optional.of(reversed);
    }
}
