package com.lms.decision.domain;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.math.RoundingMode;

/**
 * The eligibility rule DSL (phase prompt S5): a sealed set of typed rules, each carrying its
 * thresholds from the versioned rule-set JSON (never hard-coded in Java — CLAUDE.md global do-not
 * list). A rule is a pure comparison over {@link DecisionInputs} producing a {@link RuleResult}.
 */
public sealed interface Rule {

    String id();

    RuleResult evaluate(DecisionInputs inputs);

    /** Applicant age must be within [minAge, maxAge]. */
    record AgeRule(int minAge, int maxAge) implements Rule {
        @Override
        public String id() {
            return "AGE";
        }

        @Override
        public RuleResult evaluate(DecisionInputs in) {
            boolean ok = in.ageYears() >= minAge && in.ageYears() <= maxAge;
            return ok
                    ? RuleResult.pass(
                            id(), "age " + in.ageYears() + " in [" + minAge + "," + maxAge + "]")
                    : RuleResult.fail(
                            id(),
                            ReasonCode.AGE_OUT_OF_RANGE,
                            "age " + in.ageYears() + " out of range");
        }
    }

    /** KYC must be VERIFIED. */
    record KycRule() implements Rule {
        @Override
        public String id() {
            return "KYC";
        }

        @Override
        public RuleResult evaluate(DecisionInputs in) {
            return in.kycVerified()
                    ? RuleResult.pass(id(), "KYC verified")
                    : RuleResult.fail(id(), ReasonCode.KYC_NOT_VERIFIED, "KYC not verified");
        }
    }

    /**
     * Bureau score: >= {@code minApprove} passes; [{@code minRefer}, {@code minApprove}) refers;
     * below {@code minRefer} fails. If the bureau was unavailable the rule refers with
     * BUREAU_UNAVAILABLE so the application never hangs.
     */
    record BureauScoreRule(int minApprove, int minRefer) implements Rule {
        @Override
        public String id() {
            return "BUREAU_SCORE";
        }

        @Override
        public RuleResult evaluate(DecisionInputs in) {
            if (!in.bureauAvailable()) {
                return RuleResult.refer(id(), ReasonCode.BUREAU_UNAVAILABLE, "bureau unavailable");
            }
            int score = in.bureauScore();
            if (score >= minApprove) {
                return RuleResult.pass(id(), "score " + score + " >= " + minApprove);
            }
            if (score >= minRefer) {
                return RuleResult.refer(
                        id(), ReasonCode.BUREAU_SCORE_LOW, "score " + score + " in refer band");
            }
            return RuleResult.fail(
                    id(), ReasonCode.BUREAU_SCORE_LOW, "score " + score + " below " + minRefer);
        }
    }

    /** No active overdue account at the bureau. */
    record BureauDelinquencyRule() implements Rule {
        @Override
        public String id() {
            return "BUREAU_DELINQUENCY";
        }

        @Override
        public RuleResult evaluate(DecisionInputs in) {
            if (!in.bureauAvailable()) {
                return RuleResult.refer(id(), ReasonCode.BUREAU_UNAVAILABLE, "bureau unavailable");
            }
            return in.bureauHasOverdueAccount()
                    ? RuleResult.fail(
                            id(), ReasonCode.BUREAU_DELINQUENT, "active overdue account reported")
                    : RuleResult.pass(id(), "no active overdue account");
        }
    }

    /** Declared monthly income must be at least {@code minMonthlyIncome}. */
    record IncomeRule(Money minMonthlyIncome) implements Rule {
        @Override
        public String id() {
            return "INCOME";
        }

        @Override
        public RuleResult evaluate(DecisionInputs in) {
            return in.declaredMonthlyIncome().compareTo(minMonthlyIncome) >= 0
                    ? RuleResult.pass(id(), "income >= " + minMonthlyIncome)
                    : RuleResult.fail(
                            id(), ReasonCode.INCOME_TOO_LOW, "income below " + minMonthlyIncome);
        }
    }

    /** (existing EMIs + new EMI) / income must be at most {@code maxFoirPercent}%. */
    record AffordabilityRule(int maxFoirPercent) implements Rule {
        @Override
        public String id() {
            return "AFFORDABILITY";
        }

        @Override
        public RuleResult evaluate(DecisionInputs in) {
            Money income = in.declaredMonthlyIncome();
            if (!income.isPositive()) {
                return RuleResult.fail(id(), ReasonCode.FOIR_EXCEEDED, "no income");
            }
            Money totalEmi = in.existingMonthlyEmi().add(in.newMonthlyEmi());
            Money allowed = income.percentOf(Rate.percent(maxFoirPercent), RoundingMode.HALF_UP);
            return totalEmi.compareTo(allowed) <= 0
                    ? RuleResult.pass(
                            id(), "EMI " + totalEmi + " within " + maxFoirPercent + "% FOIR")
                    : RuleResult.fail(
                            id(),
                            ReasonCode.FOIR_EXCEEDED,
                            "EMI " + totalEmi + " exceeds " + allowed);
        }
    }

    /** At most {@code maxActiveLoans} active loans with us. */
    record ActiveLoanRule(int maxActiveLoans) implements Rule {
        @Override
        public String id() {
            return "ACTIVE_LOANS";
        }

        @Override
        public RuleResult evaluate(DecisionInputs in) {
            return in.activeLoanCount() <= maxActiveLoans
                    ? RuleResult.pass(id(), in.activeLoanCount() + " active loans")
                    : RuleResult.fail(
                            id(),
                            ReasonCode.ACTIVE_LOAN_EXISTS,
                            in.activeLoanCount() + " active loans");
        }
    }

    /** At most {@code maxPerWindow} applications per phone in the velocity window (inclusive). */
    record VelocityRule(int maxPerWindow) implements Rule {
        @Override
        public String id() {
            return "VELOCITY";
        }

        @Override
        public RuleResult evaluate(DecisionInputs in) {
            return in.applicationsInWindow() <= maxPerWindow
                    ? RuleResult.pass(id(), in.applicationsInWindow() + " applications in window")
                    : RuleResult.fail(
                            id(),
                            ReasonCode.VELOCITY_BREACH,
                            in.applicationsInWindow() + " applications exceed " + maxPerWindow);
        }
    }
}
