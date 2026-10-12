package com.lms.decision.application;

import com.lms.decision.application.port.out.DecisionEventPublisher;
import com.lms.decision.application.port.out.DecisionRepository;
import com.lms.decision.application.port.out.MarketDataProvider;
import com.lms.decision.application.port.out.RuleSetRepository;
import com.lms.decision.application.port.out.VelocityWindow;
import com.lms.decision.domain.BureauSnapshot;
import com.lms.decision.domain.Decision;
import com.lms.decision.domain.DecisionEvaluator;
import com.lms.decision.domain.DecisionId;
import com.lms.decision.domain.DecisionInputs;
import com.lms.decision.domain.DecisionResult;
import com.lms.decision.domain.Outcome;
import com.lms.decision.domain.RuleSet;
import com.lms.money.Money;
import com.lms.money.Rate;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a decision (phase prompt S5): velocity count → bureau/bank fan-out → assemble inputs
 * → evaluate the current rule set → persist the full, explainable decision → publish {@code
 * decision.completed}. The save and the outbox write share one transaction so the event cannot be
 * lost or sent without the decision being stored.
 *
 * <p>Idempotency (one decision per {@code application.submitted}) is enforced by the inbox in the
 * Kafka adapter; this service is the pure orchestration and is unit-tested with fakes.
 */
@Service
public class DecisionService {

    private final RuleSetRepository ruleSets;
    private final MarketDataProvider marketData;
    private final VelocityWindow velocity;
    private final DecisionRepository repository;
    private final DecisionEventPublisher events;
    private final Clock clock;
    private final Rate assessmentApr;
    private final int assessmentTenor;

    public DecisionService(
            RuleSetRepository ruleSets,
            MarketDataProvider marketData,
            VelocityWindow velocity,
            DecisionRepository repository,
            DecisionEventPublisher events,
            Clock clock,
            @Value("${lms.decision.assessment.apr-percent:24}") long assessmentAprPercent,
            @Value("${lms.decision.assessment.tenor-months:12}") int assessmentTenor) {
        this.ruleSets = ruleSets;
        this.marketData = marketData;
        this.velocity = velocity;
        this.repository = repository;
        this.events = events;
        this.clock = clock;
        this.assessmentApr = Rate.percent(assessmentAprPercent);
        this.assessmentTenor = assessmentTenor;
    }

    @Transactional
    public Decision decide(ApplicationForDecision app) {
        int inWindow = velocity.recordAndCount(app.phoneBlindIndex());
        MarketData market = marketData.fetch(app.subjectId(), app.phone());
        DecisionInputs inputs =
                DecisionInputsAssembler.assemble(
                        app, market, inWindow, assessmentApr, assessmentTenor);
        RuleSet ruleSet = ruleSets.current();
        DecisionResult result = DecisionEvaluator.evaluate(ruleSet, inputs, app.requestedAmount());

        Decision decision =
                new Decision(
                        DecisionId.newId(clock),
                        app.applicationId(),
                        app.customerId(),
                        app.requestedAmount(),
                        result.outcome(),
                        result.reasonCodes(),
                        result.ruleResults(),
                        inputs,
                        snapshot(market),
                        result.offeredLimit(),
                        result.ruleSetVersion(),
                        false,
                        Optional.empty(),
                        clock.instant());
        repository.save(decision);
        events.decisionCompleted(decision);
        return decision;
    }

    /**
     * Record an admin manual-review override (phase prompt S5): a new decision anchored on the
     * latest one for the application, with the overriding outcome, a review note, and (for APPROVE)
     * the admin-set offered limit. Publishes a fresh {@code decision.completed}.
     */
    @Transactional
    public Decision review(
            String applicationId,
            Outcome overrideOutcome,
            String note,
            Optional<Money> overrideLimit) {
        Decision prior =
                repository
                        .findLatestByApplication(applicationId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "no decision to review for application "
                                                        + applicationId));
        Optional<Money> limit;
        List<com.lms.decision.domain.ReasonCode> reasonCodes;
        if (overrideOutcome == Outcome.APPROVE) {
            limit =
                    Optional.of(
                            overrideLimit.orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "an APPROVE override must specify an offered limit")));
            reasonCodes = List.of();
        } else {
            limit = Optional.empty();
            reasonCodes = prior.reasonCodes();
        }

        Decision overridden =
                new Decision(
                        DecisionId.newId(clock),
                        prior.applicationId(),
                        prior.customerId(),
                        prior.requestedAmount(),
                        overrideOutcome,
                        reasonCodes,
                        prior.ruleResults(),
                        prior.inputs(),
                        prior.bureauSnapshot(),
                        limit,
                        prior.ruleSetVersion(),
                        true,
                        Optional.of(note),
                        clock.instant());
        repository.save(overridden);
        events.decisionCompleted(overridden);
        return overridden;
    }

    private static BureauSnapshot snapshot(MarketData market) {
        return new BureauSnapshot(
                market.bureauAvailable(),
                market.bureauScore(),
                market.bureauOverdue(),
                market.activeAccounts(),
                market.bankEstimatedIncome());
    }
}
