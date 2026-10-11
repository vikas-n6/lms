package com.lms.decision.domain;

import com.lms.money.Money;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates a {@link RuleSet} against {@link DecisionInputs} to a {@link DecisionResult} (phase
 * prompt S5). Pure and deterministic: the same (rule set, inputs, requested amount) always yields
 * an identical result, which is what makes a stored decision replayable.
 *
 * <p>Aggregation: any FAIL makes the outcome REJECT; otherwise any REFER makes it REFER; otherwise
 * APPROVE with the offered limit. Reason codes preserve rule order and are deduplicated.
 */
public final class DecisionEvaluator {

    private DecisionEvaluator() {}

    public static DecisionResult evaluate(
            RuleSet ruleSet, DecisionInputs inputs, Money requestedAmount) {
        List<RuleResult> results = new ArrayList<>(ruleSet.rules().size());
        for (Rule rule : ruleSet.rules()) {
            results.add(rule.evaluate(inputs));
        }

        LinkedHashSet<ReasonCode> fails = new LinkedHashSet<>();
        LinkedHashSet<ReasonCode> refers = new LinkedHashSet<>();
        for (RuleResult r : results) {
            switch (r.status()) {
                case FAIL -> fails.add(r.reasonCode());
                case REFER -> refers.add(r.reasonCode());
                case PASS -> {
                    /* no reason code */
                }
            }
        }

        if (!fails.isEmpty()) {
            return new DecisionResult(
                    Outcome.REJECT,
                    List.copyOf(fails),
                    results,
                    Optional.empty(),
                    ruleSet.version());
        }
        if (!refers.isEmpty()) {
            return new DecisionResult(
                    Outcome.REFER,
                    List.copyOf(refers),
                    results,
                    Optional.empty(),
                    ruleSet.version());
        }
        Money offered =
                ruleSet.limitPolicy()
                        .offeredLimit(
                                inputs.bureauScore(),
                                inputs.declaredMonthlyIncome(),
                                requestedAmount);
        return new DecisionResult(
                Outcome.APPROVE, List.of(), results, Optional.of(offered), ruleSet.version());
    }
}
