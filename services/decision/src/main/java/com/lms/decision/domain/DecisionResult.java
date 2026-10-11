package com.lms.decision.domain;

import com.lms.money.Money;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The full, explainable result of evaluating a {@link RuleSet} against {@link DecisionInputs}: the
 * outcome, the deduplicated reason codes, every rule's result, the offered limit (present only on
 * APPROVE) and the rule-set version. Everything needed for the explainer and for replay.
 */
public record DecisionResult(
        Outcome outcome,
        List<ReasonCode> reasonCodes,
        List<RuleResult> ruleResults,
        Optional<Money> offeredLimit,
        String ruleSetVersion) {

    public DecisionResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(offeredLimit, "offeredLimit");
        Objects.requireNonNull(ruleSetVersion, "ruleSetVersion");
        reasonCodes = List.copyOf(reasonCodes);
        ruleResults = List.copyOf(ruleResults);
        if (outcome == Outcome.APPROVE && offeredLimit.isEmpty()) {
            throw new IllegalArgumentException("an APPROVE decision must carry an offered limit");
        }
        if (outcome != Outcome.APPROVE && offeredLimit.isPresent()) {
            throw new IllegalArgumentException("only an APPROVE decision carries an offered limit");
        }
    }
}
