package com.lms.decision.adapters.out.ruleset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.decision.domain.DecisionEvaluator;
import com.lms.decision.domain.DecisionInputs;
import com.lms.decision.domain.Outcome;
import com.lms.decision.domain.RuleSet;
import com.lms.money.Money;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class RuleSetJsonLoaderTest {

    private static final Currency INR = Currency.getInstance("INR");
    private final RuleSetJsonLoader loader = new RuleSetJsonLoader(new ObjectMapper());

    private static final String V1 =
            """
            { "version":"v1", "currency":"INR",
              "rules":[
                {"type":"AGE","minAge":21,"maxAge":60},
                {"type":"KYC"},
                {"type":"BUREAU_SCORE","minApprove":650,"minRefer":630},
                {"type":"BUREAU_DELINQUENCY"},
                {"type":"INCOME","minMonthlyIncomeMinor":1500000},
                {"type":"AFFORDABILITY","maxFoirPercent":50},
                {"type":"ACTIVE_LOANS","maxActiveLoans":1},
                {"type":"VELOCITY","maxPerWindow":3}],
              "limitPolicy":{"incomeMultiple":3,"bands":[
                {"minScore":650,"maxScore":699,"maxAmountMinor":1500000},
                {"minScore":700,"maxScore":749,"maxAmountMinor":3000000},
                {"minScore":750,"maxScore":900,"maxAmountMinor":5000000}]}}
            """;

    @Test
    void parsesVersionRulesAndLimitPolicy() {
        RuleSet rs = loader.load(V1);
        assertThat(rs.version()).isEqualTo("v1");
        assertThat(rs.rules()).hasSize(8);
        assertThat(rs.limitPolicy().incomeMultiple()).isEqualTo(3);
        assertThat(rs.limitPolicy().bands()).hasSize(3);
    }

    @Test
    void parsedRuleSetApprovesAQualifyingApplicant() {
        RuleSet rs = loader.load(V1);
        DecisionInputs in =
                new DecisionInputs(
                        34,
                        true,
                        true,
                        760,
                        false,
                        Money.of(5_000_000L, INR),
                        Money.of(0L, INR),
                        Money.of(300_000L, INR),
                        0,
                        1);
        assertThat(DecisionEvaluator.evaluate(rs, in, Money.of(5_000_000L, INR)).outcome())
                .isEqualTo(Outcome.APPROVE);
    }

    @Test
    void rejectsUnknownRuleType() {
        assertThatThrownBy(
                        () ->
                                loader.load(
                                        "{\"version\":\"x\",\"currency\":\"INR\",\"rules\":[{\"type\":\"NOPE\"}],\"limitPolicy\":{\"incomeMultiple\":3,\"bands\":[]}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> loader.load("{ not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
