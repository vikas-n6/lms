package com.lms.decision.domain;

import com.lms.money.Money;
import java.util.Currency;
import java.util.List;

/** The domain-rules v1 default rule set (INR), used across the decision domain tests. */
final class TestRuleSets {

    static final Currency INR = Currency.getInstance("INR");

    private TestRuleSets() {}

    static Money inr(long minor) {
        return Money.of(minor, INR);
    }

    static RuleSet defaultV1() {
        return new RuleSet(
                "v1",
                List.of(
                        new Rule.AgeRule(21, 60),
                        new Rule.KycRule(),
                        new Rule.BureauScoreRule(650, 630),
                        new Rule.BureauDelinquencyRule(),
                        new Rule.IncomeRule(inr(1_500_000L)), // 15,000.00
                        new Rule.AffordabilityRule(50),
                        new Rule.ActiveLoanRule(1),
                        new Rule.VelocityRule(3)),
                new LimitPolicy(
                        List.of(
                                new LimitPolicy.Band(650, 699, inr(1_500_000L)), // up to 15,000.00
                                new LimitPolicy.Band(700, 749, inr(3_000_000L)), // up to 30,000.00
                                new LimitPolicy.Band(750, 900, inr(5_000_000L))), // up to 50,000.00
                        3)); // capped at 3x monthly income
    }

    /** A fully-qualifying applicant; individual tests tweak one field to exercise a rule. */
    static DecisionInputs qualifyingInputs() {
        return new DecisionInputs(
                34, // age
                true, // KYC verified
                true, // bureau available
                760, // score
                false, // no overdue
                inr(5_000_000L), // income 50,000.00
                inr(0L), // existing EMI
                inr(300_000L), // new EMI 3,000.00
                0, // active loans
                1); // applications in window (this one)
    }
}
