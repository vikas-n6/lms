package com.lms.decision.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.decision.domain.DecisionEvaluator;
import com.lms.decision.domain.DecisionInputs;
import com.lms.decision.domain.Outcome;
import com.lms.decision.domain.RuleSet;
import com.lms.money.Money;
import com.lms.money.Rate;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecisionInputsAssemblerTest {

    private static final Currency INR = Currency.getInstance("INR");

    private static ApplicationForDecision app(Money requested, Money income) {
        return new ApplicationForDecision(
                "app-1",
                "cust-1",
                "cust-1",
                "9000000001",
                requested,
                34,
                true,
                income,
                Money.zero(INR),
                0);
    }

    @Test
    void mapsFactsMarketDataAndComputesAssessmentEmi() {
        MarketData market =
                new MarketData(true, 760, false, 1, Optional.of(Money.of(5_000_000L, INR)));
        DecisionInputs in =
                DecisionInputsAssembler.assemble(
                        app(Money.of(5_000_000L, INR), Money.of(5_000_000L, INR)),
                        market,
                        2,
                        Rate.percent(24),
                        12);

        assertThat(in.ageYears()).isEqualTo(34);
        assertThat(in.kycVerified()).isTrue();
        assertThat(in.bureauAvailable()).isTrue();
        assertThat(in.bureauScore()).isEqualTo(760);
        assertThat(in.applicationsInWindow()).isEqualTo(2);
        // 50,000.00 over 12 months at 24% APR => EMI 4,727.98.
        assertThat(in.newMonthlyEmi()).isEqualTo(Money.of(472_798L, INR));
    }

    @Test
    void bureauUnavailableFlowsThroughToRefer() {
        MarketData market = MarketData.bureauUnavailable(Optional.empty());
        DecisionInputs in =
                DecisionInputsAssembler.assemble(
                        app(Money.of(3_000_000L, INR), Money.of(5_000_000L, INR)),
                        market,
                        1,
                        Rate.percent(24),
                        12);
        assertThat(in.bureauAvailable()).isFalse();

        RuleSet v1 =
                new com.lms.decision.adapters.out.ruleset.RuleSetJsonLoader(
                                new com.fasterxml.jackson.databind.ObjectMapper())
                        .load(
                                """
                                {"version":"v1","currency":"INR","rules":[
                                  {"type":"AGE","minAge":21,"maxAge":60},
                                  {"type":"KYC"},
                                  {"type":"BUREAU_SCORE","minApprove":650,"minRefer":630},
                                  {"type":"BUREAU_DELINQUENCY"},
                                  {"type":"INCOME","minMonthlyIncomeMinor":1500000},
                                  {"type":"AFFORDABILITY","maxFoirPercent":50},
                                  {"type":"ACTIVE_LOANS","maxActiveLoans":1},
                                  {"type":"VELOCITY","maxPerWindow":3}],
                                 "limitPolicy":{"incomeMultiple":3,"bands":[
                                  {"minScore":650,"maxScore":900,"maxAmountMinor":5000000}]}}
                                """);
        assertThat(DecisionEvaluator.evaluate(v1, in, Money.of(3_000_000L, INR)).outcome())
                .isEqualTo(Outcome.REFER);
    }
}
