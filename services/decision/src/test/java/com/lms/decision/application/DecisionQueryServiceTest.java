package com.lms.decision.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.decision.application.port.out.DecisionRepository;
import com.lms.decision.domain.BureauSnapshot;
import com.lms.decision.domain.Decision;
import com.lms.decision.domain.DecisionId;
import com.lms.decision.domain.DecisionInputs;
import com.lms.decision.domain.Outcome;
import com.lms.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecisionQueryServiceTest {

    private static final Currency INR = Currency.getInstance("INR");

    private static Decision sampleReject(DecisionId id) {
        DecisionInputs inputs =
                new DecisionInputs(
                        34,
                        true,
                        true,
                        540,
                        false,
                        Money.of(5_000_000L, INR),
                        Money.zero(INR),
                        Money.of(300_000L, INR),
                        0,
                        1);
        return new Decision(
                id,
                "app-1",
                "cust-1",
                Money.of(3_000_000L, INR),
                Outcome.REJECT,
                List.of(),
                List.of(),
                inputs,
                new BureauSnapshot(true, 540, false, 0, Optional.empty()),
                Optional.empty(),
                "v1",
                false,
                Optional.empty(),
                Instant.parse("2026-10-12T10:00:00Z"));
    }

    @Test
    void returnsTheDecisionWhenPresentAndThrowsOtherwise() {
        DecisionId id = DecisionId.of("018f9a00-0000-7000-8000-000000000001");
        Decision decision = sampleReject(id);
        DecisionRepository repo =
                new DecisionRepository() {
                    @Override
                    public void save(Decision d) {}

                    @Override
                    public Optional<Decision> findById(DecisionId queried) {
                        return queried.equals(id) ? Optional.of(decision) : Optional.empty();
                    }

                    @Override
                    public Optional<Decision> findLatestByApplication(String applicationId) {
                        return Optional.empty();
                    }
                };
        DecisionQueryService service = new DecisionQueryService(repo);

        assertThat(service.byId(id.toString())).isSameAs(decision);
        assertThatThrownBy(() -> service.byId("018f9a00-0000-7000-8000-0000000000ff"))
                .isInstanceOf(DecisionNotFoundException.class);
    }
}
