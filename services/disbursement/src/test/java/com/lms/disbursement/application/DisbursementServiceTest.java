package com.lms.disbursement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.disbursement.application.port.out.CompliancePolicy;
import com.lms.disbursement.application.port.out.DisbursementEventPublisher;
import com.lms.disbursement.application.port.out.DisbursementRepository;
import com.lms.disbursement.application.port.out.PayoutAttemptRepository;
import com.lms.disbursement.application.port.out.RailClient;
import com.lms.disbursement.domain.Disbursement;
import com.lms.disbursement.domain.DisbursementStatus;
import com.lms.disbursement.domain.PayoutAttempt;
import com.lms.money.Money;
import com.lms.testkit.time.MutableClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisbursementServiceTest {

    private static final Currency INR = Currency.getInstance("INR");
    private final MutableClock clock = MutableClock.utc(Instant.parse("2026-10-20T05:00:00Z"));
    private final FakeRepo repo = new FakeRepo();
    private final FakeAttempts attempts = new FakeAttempts();
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final StubRail rail = new StubRail();
    private final DisbursementService service =
            new DisbursementService(
                    repo,
                    attempts,
                    publisher,
                    rail,
                    loanId -> CompliancePolicy.ComplianceResult.allow(),
                    clock,
                    "http://localhost:8105/callbacks/rail");

    private static Money inr(long minor) {
        return Money.of(minor, INR);
    }

    private Disbursement seed() {
        Disbursement d =
                Disbursement.initiate(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "LN-1",
                        inr(5_000_000L),
                        inr(100_000L),
                        inr(18_000L),
                        inr(4_882_000L),
                        clock.instant());
        repo.save(d);
        return d;
    }

    @Test
    void recordInitiatedPublishesOnceAndIsIdempotent() {
        Disbursement d = seed();
        service.recordInitiated(d.id());
        service.recordInitiated(d.id()); // second call must not publish again

        assertThat(publisher.initiated).isEqualTo(1);
        assertThat(repo.byId(d.id()).initiatedPublished()).isTrue();
    }

    @Test
    void startAttemptRecordsAttemptAndIncrementsCount() {
        Disbursement d = seed();
        String key = service.startAttempt(d.id(), 1);

        assertThat(key).isEqualTo(PayoutAttempt.idempotencyKey(d.id(), 1));
        assertThat(attempts.saved).hasSize(1);
        assertThat(repo.byId(d.id()).attempts()).isEqualTo(1);
    }

    @Test
    void recordSucceededTransitionsAndPublishes() {
        Disbursement d = seed();
        service.recordSucceeded(d.id(), "RAIL-9", "UTR-9");

        Disbursement after = repo.byId(d.id());
        assertThat(after.status()).isEqualTo(DisbursementStatus.SUCCEEDED);
        assertThat(after.utr()).isEqualTo("UTR-9");
        assertThat(after.disbursementDate()).isNotNull();
        assertThat(publisher.succeeded).isEqualTo(1);
    }

    @Test
    void recordFailedTransitionsAndPublishes() {
        Disbursement d = seed();
        service.recordFailed(d.id(), "RAIL_DECLINED");

        assertThat(repo.byId(d.id()).status()).isEqualTo(DisbursementStatus.FAILED);
        assertThat(publisher.failed).isEqualTo(1);
    }

    @Test
    void callRailBuildsRequestFromNetAmount() {
        Disbursement d = seed();
        service.callRail(d.id(), "k1");
        assertThat(rail.lastRequest.amountMinor()).isEqualTo(4_882_000L);
        assertThat(rail.lastRequest.currency()).isEqualTo("INR");
    }

    @Test
    void loadMissingThrows() {
        assertThatThrownBy(() -> service.recordFailed(UUID.randomUUID(), "x"))
                .isInstanceOf(DisbursementNotFoundException.class);
    }

    private static final class FakeRepo implements DisbursementRepository {
        private final Map<UUID, Disbursement> byId = new HashMap<>();

        Disbursement byId(UUID id) {
            return byId.get(id);
        }

        @Override
        public void save(Disbursement d) {
            byId.put(d.id(), d);
        }

        @Override
        public Optional<Disbursement> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Disbursement> findByLoanId(UUID loanId) {
            return byId.values().stream().filter(d -> d.loanId().equals(loanId)).findFirst();
        }

        @Override
        public List<Disbursement> findAll() {
            return List.copyOf(byId.values());
        }
    }

    private static final class FakeAttempts implements PayoutAttemptRepository {
        final List<PayoutAttempt> saved = new ArrayList<>();

        @Override
        public void save(PayoutAttempt attempt) {
            saved.add(attempt);
        }

        @Override
        public Optional<PayoutAttempt> find(UUID disbursementId, int attemptNumber) {
            return saved.stream()
                    .filter(a -> a.disbursementId().equals(disbursementId))
                    .filter(a -> a.attemptNumber() == attemptNumber)
                    .findFirst();
        }

        @Override
        public List<PayoutAttempt> findByDisbursementId(UUID disbursementId) {
            return saved.stream().filter(a -> a.disbursementId().equals(disbursementId)).toList();
        }
    }

    private static final class RecordingPublisher implements DisbursementEventPublisher {
        int initiated;
        int succeeded;
        int failed;

        @Override
        public void initiated(Disbursement disbursement) {
            initiated++;
        }

        @Override
        public void succeeded(Disbursement disbursement) {
            succeeded++;
        }

        @Override
        public void failed(Disbursement disbursement) {
            failed++;
        }
    }

    private static final class StubRail implements RailClient {
        PayoutRequest lastRequest;

        @Override
        public Ack initiatePayout(PayoutRequest request) {
            this.lastRequest = request;
            return new Ack(false, "RAIL-1");
        }

        @Override
        public StatusResult queryStatus(String idempotencyKey) {
            return new StatusResult(Outcome.UNKNOWN, null, null, null);
        }
    }
}
