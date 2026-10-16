package com.lms.disbursement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.disbursement.adapters.out.compliance.PermissiveCompliancePolicy;
import com.lms.disbursement.application.port.out.CompliancePolicy;
import com.lms.disbursement.application.port.out.DisbursementRepository;
import com.lms.disbursement.application.port.out.PayoutAttemptRepository;
import com.lms.disbursement.domain.Disbursement;
import com.lms.disbursement.domain.PayoutAttempt;
import com.lms.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisbursementQueryServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-20T05:00:00Z");

    private static Money inr(long minor) {
        return Money.of(minor, Currency.getInstance("INR"));
    }

    private static Disbursement d(UUID id) {
        return Disbursement.initiate(
                id,
                UUID.randomUUID(),
                "LN-1",
                inr(5_000_000L),
                inr(100_000L),
                inr(18_000L),
                inr(4_882_000L),
                T0);
    }

    @Test
    void listAndGetAndAttemptsDelegate() {
        UUID id = UUID.randomUUID();
        Disbursement disbursement = d(id);
        DisbursementQueryService service =
                new DisbursementQueryService(new OneRepo(disbursement), new OneAttempt(id));

        assertThat(service.list()).containsExactly(disbursement);
        assertThat(service.get(id)).isEqualTo(disbursement);
        assertThat(service.attempts(id)).hasSize(1);
    }

    @Test
    void getMissingThrows() {
        DisbursementQueryService service =
                new DisbursementQueryService(
                        new OneRepo(d(UUID.randomUUID())), new OneAttempt(UUID.randomUUID()));
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(DisbursementNotFoundException.class);
    }

    @Test
    void permissivePolicyAllows() {
        CompliancePolicy policy = new PermissiveCompliancePolicy();
        assertThat(policy.check(UUID.randomUUID()).allowed()).isTrue();
    }

    private record OneRepo(Disbursement only) implements DisbursementRepository {
        @Override
        public void save(Disbursement disbursement) {}

        @Override
        public Optional<Disbursement> findById(UUID id) {
            return only.id().equals(id) ? Optional.of(only) : Optional.empty();
        }

        @Override
        public Optional<Disbursement> findByLoanId(UUID loanId) {
            return only.loanId().equals(loanId) ? Optional.of(only) : Optional.empty();
        }

        @Override
        public List<Disbursement> findAll() {
            return List.of(only);
        }
    }

    private record OneAttempt(UUID disbursementId) implements PayoutAttemptRepository {
        @Override
        public void save(PayoutAttempt attempt) {}

        @Override
        public Optional<PayoutAttempt> find(UUID d, int n) {
            return Optional.empty();
        }

        @Override
        public List<PayoutAttempt> findByDisbursementId(UUID d) {
            return List.of(
                    new PayoutAttempt(
                            UUID.randomUUID(),
                            d,
                            1,
                            "k",
                            PayoutAttempt.Status.PENDING,
                            null,
                            Instant.parse("2026-10-20T05:00:00Z"),
                            null));
        }
    }
}
