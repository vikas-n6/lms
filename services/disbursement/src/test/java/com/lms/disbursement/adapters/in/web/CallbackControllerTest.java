package com.lms.disbursement.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.disbursement.application.CallbackClassifier;
import com.lms.disbursement.application.port.out.DisbursementRepository;
import com.lms.disbursement.application.port.out.RailCallbackDedupStore;
import com.lms.disbursement.application.port.out.WorkflowStarter;
import com.lms.disbursement.domain.Disbursement;
import com.lms.money.Money;
import com.lms.security.callback.CallbackSigner;
import com.lms.security.callback.CallbackVerifier;
import com.lms.security.callback.InvalidCallbackSignatureException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CallbackControllerTest {

    private static final String SECRET = "local-dev-callback-secret";
    private static final Instant NOW = Instant.parse("2026-10-20T06:00:00Z");
    private static final UUID LOAN = UUID.fromString("018f9a00-0000-7000-8000-0000000000aa");

    private final CallbackSigner signer = new CallbackSigner(SECRET.getBytes());
    private final CallbackVerifier verifier =
            CallbackVerifier.fromSecret(
                    SECRET, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
    private final FakeRepo repo = new FakeRepo();
    private final FakeDedup dedup = new FakeDedup();
    private final RecordingWorkflows workflows = new RecordingWorkflows();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final CallbackController controller =
            new CallbackController(
                    verifier,
                    new CallbackClassifier(repo, dedup),
                    workflows,
                    JsonMapper.builder().build(),
                    meters);

    private static Money inr(long minor) {
        return Money.of(minor, Currency.getInstance("INR"));
    }

    private Disbursement initiated() {
        return Disbursement.initiate(
                UUID.randomUUID(),
                LOAN,
                "LN-1",
                inr(5_000_000L),
                inr(100_000L),
                inr(18_000L),
                inr(4_882_000L),
                NOW);
    }

    private String body(String status) {
        return "{\"railRef\":\"RAIL-9\",\"idempotencyKey\":\"disb-x-attempt-1\",\"loanId\":\""
                + LOAN
                + "\",\"status\":\""
                + status
                + "\",\"utr\":\"UTR-9\",\"failureCode\":null}";
    }

    @Test
    void invalidSignatureIsRejected() {
        repo.put(initiated());
        assertThatThrownBy(() -> controller.rail("t=1,v1=bad", body("SUCCESS")))
                .isInstanceOf(InvalidCallbackSignatureException.class);
        assertThat(workflows.signals).isEmpty();
    }

    @Test
    void firstSuccessSignalsTheWorkflow() {
        repo.put(initiated());
        String b = body("SUCCESS");
        controller.rail(signer.sign(NOW, b), b);

        assertThat(workflows.signals).hasSize(1);
        assertThat(workflows.signals.get(0).success()).isTrue();
        assertThat(workflows.signals.get(0).utr()).isEqualTo("UTR-9");
    }

    @Test
    void duplicateCallbackDoesNotSignal() {
        repo.put(initiated());
        dedup.firstSeen = false; // already seen (railRef, status)
        String b = body("SUCCESS");
        controller.rail(signer.sign(NOW, b), b);

        assertThat(workflows.signals).isEmpty();
    }

    @Test
    void conflictingTerminalCallbackIsCountedAndNotSignalled() {
        repo.put(initiated().succeeded("RAIL-9", "UTR-9", LocalDate.of(2026, 10, 20), NOW));
        String b = body("FAILED"); // failure after success already recorded
        controller.rail(signer.sign(NOW, b), b);

        assertThat(workflows.signals).isEmpty();
        assertThat(meters.counter("lms.disbursement.callback.conflicts").count()).isEqualTo(1.0);
    }

    private static final class FakeRepo implements DisbursementRepository {
        private final Map<UUID, Disbursement> byLoan = new HashMap<>();

        void put(Disbursement d) {
            byLoan.put(d.loanId(), d);
        }

        @Override
        public void save(Disbursement disbursement) {
            byLoan.put(disbursement.loanId(), disbursement);
        }

        @Override
        public Optional<Disbursement> findById(UUID id) {
            return byLoan.values().stream().filter(d -> d.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Disbursement> findByLoanId(UUID loanId) {
            return Optional.ofNullable(byLoan.get(loanId));
        }

        @Override
        public List<Disbursement> findAll() {
            return List.copyOf(byLoan.values());
        }
    }

    private static final class FakeDedup implements RailCallbackDedupStore {
        boolean firstSeen = true;

        @Override
        public boolean firstSeen(String railRef, String status) {
            return firstSeen;
        }
    }

    private static final class RecordingWorkflows implements WorkflowStarter {
        final List<RailSignal> signals = new ArrayList<>();

        @Override
        public void startDisbursement(UUID disbursementId, UUID loanId, String loanNumber) {}

        @Override
        public void signalRailResult(UUID loanId, RailSignal signal) {
            signals.add(signal);
        }

        @Override
        public void signalAdminDecision(UUID loanId, boolean retry) {}
    }
}
