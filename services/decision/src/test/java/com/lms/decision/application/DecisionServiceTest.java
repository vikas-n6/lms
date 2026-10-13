package com.lms.decision.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.decision.adapters.out.ruleset.ClasspathRuleSetRepository;
import com.lms.decision.application.port.out.DecisionEventPublisher;
import com.lms.decision.application.port.out.DecisionRepository;
import com.lms.decision.application.port.out.MarketDataProvider;
import com.lms.decision.application.port.out.VelocityWindow;
import com.lms.decision.domain.Decision;
import com.lms.decision.domain.DecisionId;
import com.lms.decision.domain.Outcome;
import com.lms.decision.domain.ReasonCode;
import com.lms.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecisionServiceTest {

    private static final Currency INR = Currency.getInstance("INR");

    private final ClasspathRuleSetRepository ruleSets =
            new ClasspathRuleSetRepository(new ObjectMapper(), "v1");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);

    private static final class FakeRepo implements DecisionRepository {
        final List<Decision> saved = new ArrayList<>();

        @Override
        public void save(Decision decision) {
            saved.add(decision);
        }

        @Override
        public Optional<Decision> findById(DecisionId id) {
            return saved.stream().filter(d -> d.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Decision> findLatestByApplication(String applicationId) {
            Decision last = null;
            for (Decision d : saved) {
                if (d.applicationId().equals(applicationId)) {
                    last = d;
                }
            }
            return Optional.ofNullable(last);
        }

        @Override
        public List<Decision> findReferQueue(int limit) {
            return saved.stream().filter(d -> d.outcome() == Outcome.REFER).toList();
        }
    }

    private static final class FakePublisher implements DecisionEventPublisher {
        final List<Decision> published = new ArrayList<>();

        @Override
        public void decisionCompleted(Decision decision) {
            published.add(decision);
        }
    }

    private final FakeRepo repo = new FakeRepo();
    private final FakePublisher publisher = new FakePublisher();

    private DecisionService service(MarketData market, int velocityCount) {
        MarketDataProvider provider = (subjectId, phone) -> market;
        VelocityWindow velocity = key -> velocityCount;
        return new DecisionService(ruleSets, provider, velocity, repo, publisher, clock, 24, 12);
    }

    private static ApplicationForDecision app() {
        return new ApplicationForDecision(
                "app-1",
                "cust-1",
                "cust-1",
                "9000000001",
                "blind-1",
                Money.of(5_000_000L, INR),
                34,
                true,
                Money.of(5_000_000L, INR),
                Money.zero(INR),
                0);
    }

    private static MarketData market(int score, boolean overdue, boolean available) {
        return new MarketData(available, score, overdue, 1, Optional.of(Money.of(5_000_000L, INR)));
    }

    @Test
    void approvePersistsAndPublishesWithLimit() {
        Decision d = service(market(760, false, true), 1).decide(app());
        assertThat(d.outcome()).isEqualTo(Outcome.APPROVE);
        assertThat(d.offeredLimit()).isPresent();
        assertThat(repo.saved).containsExactly(d);
        assertThat(publisher.published).containsExactly(d);
        assertThat(d.ruleSetVersion()).isEqualTo("v1");
        assertThat(d.ruleResults()).isNotEmpty();
    }

    @Test
    void lowScoreRejects() {
        Decision d = service(market(540, false, true), 1).decide(app());
        assertThat(d.outcome()).isEqualTo(Outcome.REJECT);
        assertThat(d.reasonCodes()).contains(ReasonCode.BUREAU_SCORE_LOW);
    }

    @Test
    void referBandRefers() {
        assertThat(service(market(640, false, true), 1).decide(app()).outcome())
                .isEqualTo(Outcome.REFER);
    }

    @Test
    void velocityBreachRejects() {
        Decision d = service(market(760, false, true), 4).decide(app());
        assertThat(d.outcome()).isEqualTo(Outcome.REJECT);
        assertThat(d.reasonCodes()).contains(ReasonCode.VELOCITY_BREACH);
    }

    @Test
    void bureauUnavailableRefers() {
        Decision d = service(market(0, false, false), 1).decide(app());
        assertThat(d.outcome()).isEqualTo(Outcome.REFER);
        assertThat(d.reasonCodes()).contains(ReasonCode.BUREAU_UNAVAILABLE);
        assertThat(d.bureauSnapshot().available()).isFalse();
    }

    @Test
    void reviewOverridesAReferToApproveWithAdminLimit() {
        DecisionService service = service(market(640, false, true), 1);
        service.decide(app()); // REFER
        Decision overridden =
                service.review(
                        "app-1",
                        Outcome.APPROVE,
                        "manual check passed",
                        Optional.of(Money.of(1_500_000L, INR)));
        assertThat(overridden.manualOverride()).isTrue();
        assertThat(overridden.outcome()).isEqualTo(Outcome.APPROVE);
        assertThat(overridden.offeredLimit()).contains(Money.of(1_500_000L, INR));
        assertThat(overridden.reviewNote()).contains("manual check passed");
        assertThat(publisher.published).hasSize(2); // original + override
    }

    @Test
    void approveOverrideWithoutLimitIsRejected() {
        DecisionService service = service(market(640, false, true), 1);
        service.decide(app());
        assertThatThrownBy(() -> service.review("app-1", Outcome.APPROVE, "oops", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
