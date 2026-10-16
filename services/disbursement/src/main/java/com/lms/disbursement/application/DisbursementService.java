package com.lms.disbursement.application;

import com.lms.disbursement.application.port.out.CompliancePolicy;
import com.lms.disbursement.application.port.out.DisbursementEventPublisher;
import com.lms.disbursement.application.port.out.DisbursementRepository;
import com.lms.disbursement.application.port.out.PayoutAttemptRepository;
import com.lms.disbursement.application.port.out.RailClient;
import com.lms.disbursement.domain.Disbursement;
import com.lms.disbursement.domain.PayoutAttempt;
import com.lms.events.Uuid7;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * The transactional operations the payout workflow drives through its activities (phase prompt S2).
 * Each is idempotent so Temporal's at-least-once execution never double-pays or double-publishes:
 * {@code recordInitiated} publishes once (guarded by {@link Disbursement#initiatedPublished()}),
 * the rail is idempotent on the per-attempt key, and the terminal records are state transitions on
 * the aggregate.
 */
public class DisbursementService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final DisbursementRepository disbursements;
    private final PayoutAttemptRepository attempts;
    private final DisbursementEventPublisher publisher;
    private final RailClient rail;
    private final CompliancePolicy compliance;
    private final Clock clock;
    private final String callbackUrl;

    public DisbursementService(
            DisbursementRepository disbursements,
            PayoutAttemptRepository attempts,
            DisbursementEventPublisher publisher,
            RailClient rail,
            CompliancePolicy compliance,
            Clock clock,
            String callbackUrl) {
        this.disbursements = Objects.requireNonNull(disbursements);
        this.attempts = Objects.requireNonNull(attempts);
        this.publisher = Objects.requireNonNull(publisher);
        this.rail = Objects.requireNonNull(rail);
        this.compliance = Objects.requireNonNull(compliance);
        this.clock = Objects.requireNonNull(clock);
        this.callbackUrl = Objects.requireNonNull(callbackUrl);
    }

    public boolean complianceApproved(UUID id) {
        return compliance.check(load(id).loanId()).allowed();
    }

    public void recordInitiated(UUID id) {
        Disbursement d = load(id);
        if (!d.initiatedPublished()) {
            publisher.initiated(d);
            disbursements.save(d.markInitiatedPublished(clock.instant()));
        }
    }

    public String startAttempt(UUID id, int attemptNumber) {
        Disbursement d = load(id);
        String key = PayoutAttempt.idempotencyKey(id, attemptNumber);
        attempts.save(
                new PayoutAttempt(
                        Uuid7.generate(clock),
                        id,
                        attemptNumber,
                        key,
                        PayoutAttempt.Status.PENDING,
                        null,
                        clock.instant(),
                        null));
        disbursements.save(d.withAttemptStarted(clock.instant()));
        return key;
    }

    public RailClient.Ack callRail(UUID id, String idempotencyKey) {
        Disbursement d = load(id);
        return rail.initiatePayout(
                new RailClient.PayoutRequest(
                        idempotencyKey,
                        d.loanId(),
                        d.net().minorUnits(),
                        d.net().currency().getCurrencyCode(),
                        callbackUrl));
    }

    public RailClient.StatusResult queryStatus(String idempotencyKey) {
        return rail.queryStatus(idempotencyKey);
    }

    public void finishAttempt(UUID id, int attemptNumber, String outcome, String failureCode) {
        attempts.find(id, attemptNumber)
                .ifPresent(
                        a ->
                                attempts.save(
                                        a.finished(
                                                PayoutAttempt.Status.valueOf(outcome),
                                                failureCode,
                                                clock.instant())));
    }

    public void recordSucceeded(UUID id, String railRef, String utr) {
        Disbursement d = load(id);
        LocalDate date = LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
        Disbursement done = d.succeeded(railRef, utr, date, clock.instant());
        disbursements.save(done);
        publisher.succeeded(done);
    }

    public void recordFailed(UUID id, String reason) {
        Disbursement done = load(id).failed(reason, clock.instant());
        disbursements.save(done);
        publisher.failed(done);
    }

    public void recordManualReview(UUID id) {
        disbursements.save(load(id).toManualReview(clock.instant()));
    }

    private Disbursement load(UUID id) {
        return disbursements.findById(id).orElseThrow(() -> new DisbursementNotFoundException(id));
    }
}
