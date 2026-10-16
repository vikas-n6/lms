package com.lms.disbursement.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.disbursement.application.RegisterDisbursementService.NewDisbursement;
import com.lms.disbursement.application.port.out.DisbursementRepository;
import com.lms.disbursement.application.port.out.WorkflowStarter;
import com.lms.disbursement.domain.Disbursement;
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

class RegisterDisbursementServiceTest {

    private static final Currency INR = Currency.getInstance("INR");
    private final MutableClock clock = MutableClock.utc(Instant.parse("2026-10-20T05:00:00Z"));
    private final FakeRepo repo = new FakeRepo();
    private final RecordingStarter starter = new RecordingStarter();
    private final RegisterDisbursementService service =
            new RegisterDisbursementService(repo, starter, clock);

    private static Money inr(long minor) {
        return Money.of(minor, INR);
    }

    private NewDisbursement command(UUID loanId) {
        return new NewDisbursement(
                loanId, "LN-1", inr(5_000_000L), inr(100_000L), inr(18_000L), inr(4_882_000L));
    }

    @Test
    void registerPersistsAndStartsTheWorkflow() {
        UUID loanId = UUID.randomUUID();
        service.register(command(loanId));

        assertThat(repo.findByLoanId(loanId)).isPresent();
        assertThat(starter.started).hasSize(1);
    }

    @Test
    void duplicateLoanIsANoOp() {
        UUID loanId = UUID.randomUUID();
        service.register(command(loanId));
        service.register(command(loanId));

        assertThat(starter.started).hasSize(1); // not started twice
    }

    private static final class FakeRepo implements DisbursementRepository {
        private final Map<UUID, Disbursement> byLoan = new HashMap<>();

        @Override
        public void save(Disbursement d) {
            byLoan.put(d.loanId(), d);
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

    private static final class RecordingStarter implements WorkflowStarter {
        final List<UUID> started = new ArrayList<>();

        @Override
        public void startDisbursement(UUID disbursementId, UUID loanId, String loanNumber) {
            started.add(loanId);
        }

        @Override
        public void signalRailResult(UUID loanId, RailSignal signal) {}

        @Override
        public void signalAdminDecision(UUID loanId, boolean retry) {}
    }
}
