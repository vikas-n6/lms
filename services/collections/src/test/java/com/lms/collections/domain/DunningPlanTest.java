package com.lms.collections.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The versioned dunning plan selects every step whose DPD threshold has been reached (phase prompt
 * task 4). Pure logic — the plan here mirrors the default configuration but the production plan is
 * loaded from {@code lms.dunning.*}.
 */
class DunningPlanTest {

    private static DunningPlan defaultPlan() {
        return new DunningPlan(
                1,
                List.of(
                        new DunningStep("DPD_1_SMS", 1, DunningActionType.SMS, "SMS", "d.dpd1"),
                        new DunningStep(
                                "DPD_3_SMS_EMAIL", 3, DunningActionType.SMS_EMAIL, "SMS", "d.dpd3"),
                        new DunningStep(
                                "DPD_7_SMS_REPRESENT",
                                7,
                                DunningActionType.SMS_REPRESENT,
                                "SMS",
                                "d.dpd7"),
                        new DunningStep(
                                "DPD_15_ESCALATE", 15, DunningActionType.ESCALATE, "NONE", ""),
                        new DunningStep(
                                "DPD_30_FORMAL",
                                30,
                                DunningActionType.FORMAL_NOTICE,
                                "EMAIL",
                                "d.dpd30"),
                        new DunningStep(
                                "DPD_60_FINAL",
                                60,
                                DunningActionType.FINAL_NOTICE,
                                "EMAIL",
                                "d.dpd60"),
                        new DunningStep("DPD_91_NPA", 91, DunningActionType.NPA_REVIEW, "NONE", ""),
                        new DunningStep(
                                "DPD_180_WRITE_OFF",
                                180,
                                DunningActionType.RECOMMEND_WRITE_OFF,
                                "NONE",
                                "")));
    }

    @Test
    void noStepsBelowTheFirstThreshold() {
        assertThat(defaultPlan().stepsReachedBy(0)).isEmpty();
    }

    @Test
    void dpd1ReachesOnlyTheFirstStep() {
        assertThat(defaultPlan().stepsReachedBy(1))
                .extracting(DunningStep::stepId)
                .containsExactly("DPD_1_SMS");
    }

    @Test
    void dpd7ReachesTheFirstThreeSteps() {
        assertThat(defaultPlan().stepsReachedBy(7))
                .extracting(DunningStep::stepId)
                .containsExactly("DPD_1_SMS", "DPD_3_SMS_EMAIL", "DPD_7_SMS_REPRESENT");
    }

    @Test
    void dpd180ReachesEveryStepInThresholdOrder() {
        assertThat(defaultPlan().stepsReachedBy(180))
                .extracting(DunningStep::dpdThreshold)
                .containsExactly(1, 3, 7, 15, 30, 60, 91, 180);
    }

    @Test
    void stepsAreSortedEvenIfConfiguredOutOfOrder() {
        DunningPlan plan =
                new DunningPlan(
                        2,
                        List.of(
                                new DunningStep(
                                        "b", 30, DunningActionType.FORMAL_NOTICE, "EMAIL", "t"),
                                new DunningStep("a", 1, DunningActionType.SMS, "SMS", "t")));
        assertThat(plan.steps()).extracting(DunningStep::stepId).containsExactly("a", "b");
    }
}
