package com.lms.decision.domain;

import static com.lms.decision.domain.TestRuleSets.inr;
import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import org.junit.jupiter.api.Test;

/**
 * Eligibility outcomes, limit bands and replay determinism (docs/domain-rules.md, phase prompt).
 */
class DecisionEvaluatorTest {

    private final RuleSet v1 = TestRuleSets.defaultV1();

    private DecisionResult evaluate(DecisionInputs in, Money requested) {
        return DecisionEvaluator.evaluate(v1, in, requested);
    }

    @Test
    void qualifyingApplicantIsApprovedWithBandAndIncomeCap() {
        DecisionResult r = evaluate(TestRuleSets.qualifyingInputs(), inr(5_000_000L));
        assertThat(r.outcome()).isEqualTo(Outcome.APPROVE);
        assertThat(r.reasonCodes()).isEmpty();
        // score 760 -> band 50,000.00; 3 x 50,000.00 income = 150,000.00; requested 50,000.00 caps.
        assertThat(r.offeredLimit()).contains(inr(5_000_000L));
    }

    @Test
    void lowScoreIsRejected() {
        DecisionInputs in =
                new DecisionInputs(
                        34, true, true, 540, false, inr(5_000_000L), inr(0L), inr(300_000L), 0, 1);
        DecisionResult r = evaluate(in, inr(3_000_000L));
        assertThat(r.outcome()).isEqualTo(Outcome.REJECT);
        assertThat(r.reasonCodes()).containsExactly(ReasonCode.BUREAU_SCORE_LOW);
        assertThat(r.offeredLimit()).isEmpty();
    }

    @Test
    void referBandScoreRefers() {
        DecisionInputs in =
                new DecisionInputs(
                        34, true, true, 640, false, inr(5_000_000L), inr(0L), inr(300_000L), 0, 1);
        DecisionResult r = evaluate(in, inr(3_000_000L));
        assertThat(r.outcome()).isEqualTo(Outcome.REFER);
        assertThat(r.reasonCodes()).containsExactly(ReasonCode.BUREAU_SCORE_LOW);
    }

    @Test
    void bureauDelinquencyIsRejected() {
        DecisionInputs in =
                new DecisionInputs(
                        34, true, true, 780, true, inr(5_000_000L), inr(0L), inr(300_000L), 0, 1);
        DecisionResult r = evaluate(in, inr(3_000_000L));
        assertThat(r.outcome()).isEqualTo(Outcome.REJECT);
        assertThat(r.reasonCodes()).contains(ReasonCode.BUREAU_DELINQUENT);
    }

    @Test
    void velocityBreachIsRejectedOnTheFourthApplication() {
        DecisionInputs in =
                new DecisionInputs(
                        34, true, true, 780, false, inr(5_000_000L), inr(0L), inr(300_000L), 0, 4);
        DecisionResult r = evaluate(in, inr(3_000_000L));
        assertThat(r.outcome()).isEqualTo(Outcome.REJECT);
        assertThat(r.reasonCodes()).contains(ReasonCode.VELOCITY_BREACH);
    }

    @Test
    void bureauUnavailableRefersAndNeverHangs() {
        DecisionInputs in =
                new DecisionInputs(
                        34, true, false, 0, false, inr(5_000_000L), inr(0L), inr(300_000L), 0, 1);
        DecisionResult r = evaluate(in, inr(3_000_000L));
        assertThat(r.outcome()).isEqualTo(Outcome.REFER);
        assertThat(r.reasonCodes()).containsExactly(ReasonCode.BUREAU_UNAVAILABLE);
    }

    @Test
    void multipleFailuresCollectAllReasonCodesInRuleOrder() {
        // Under age, KYC not verified, income too low: three FAILs, REJECT. EMI (2,000.00) stays
        // within 50% of the 5,000.00 income so affordability passes and is not a fourth reason.
        DecisionInputs in =
                new DecisionInputs(
                        18, false, true, 780, false, inr(500_000L), inr(0L), inr(200_000L), 0, 1);
        DecisionResult r = evaluate(in, inr(3_000_000L));
        assertThat(r.outcome()).isEqualTo(Outcome.REJECT);
        assertThat(r.reasonCodes())
                .containsExactly(
                        ReasonCode.AGE_OUT_OF_RANGE,
                        ReasonCode.KYC_NOT_VERIFIED,
                        ReasonCode.INCOME_TOO_LOW);
    }

    @Test
    void foirExceededIsRejected() {
        // Income 15,000.00, new EMI 10,000.00 -> FOIR 66% > 50%.
        DecisionInputs in =
                new DecisionInputs(
                        34,
                        true,
                        true,
                        780,
                        false,
                        inr(1_500_000L),
                        inr(0L),
                        inr(1_000_000L),
                        0,
                        1);
        DecisionResult r = evaluate(in, inr(3_000_000L));
        assertThat(r.outcome()).isEqualTo(Outcome.REJECT);
        assertThat(r.reasonCodes()).contains(ReasonCode.FOIR_EXCEEDED);
    }

    @Test
    void approvedLimitFollowsScoreBandIncomeAndRequestedCaps() {
        // Score 660 -> band 15,000.00; income 100,000.00 (3x=300,000.00); requested 50,000.00.
        DecisionInputs band1 =
                new DecisionInputs(
                        34, true, true, 660, false, inr(10_000_000L), inr(0L), inr(300_000L), 0, 1);
        assertThat(evaluate(band1, inr(5_000_000L)).offeredLimit()).contains(inr(1_500_000L));

        // Score 720 -> band 30,000.00; income cap higher; requested 50,000.00.
        DecisionInputs band2 =
                new DecisionInputs(
                        34, true, true, 720, false, inr(10_000_000L), inr(0L), inr(300_000L), 0, 1);
        assertThat(evaluate(band2, inr(5_000_000L)).offeredLimit()).contains(inr(3_000_000L));

        // Score 800 band 50,000.00 but income 16,000.00 -> 3x = 48,000.00 caps (income still clears
        // the 15,000.00 floor so the applicant is approvable).
        DecisionInputs incomeCapped =
                new DecisionInputs(
                        34, true, true, 800, false, inr(1_600_000L), inr(0L), inr(100_000L), 0, 1);
        assertThat(evaluate(incomeCapped, inr(5_000_000L)).offeredLimit())
                .contains(inr(4_800_000L));
    }

    @Test
    void replayIsDeterministic() {
        DecisionInputs in = TestRuleSets.qualifyingInputs();
        DecisionResult first = evaluate(in, inr(3_000_000L));
        DecisionResult second = evaluate(in, inr(3_000_000L));
        assertThat(second).isEqualTo(first);
        assertThat(second.ruleResults()).isEqualTo(first.ruleResults());
    }
}
