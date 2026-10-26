package com.lms.collections.domain;

import java.util.Objects;

/**
 * One step of a dunning plan (phase prompt task 4): an action to perform once the loan's DPD
 * reaches {@code dpdThreshold}. {@code stepId} is stable across plan versions so a {@code (caseId,
 * stepId)} row makes each action fire at most once (replays and duplicate ticks never repeat it).
 *
 * @param stepId stable identifier, e.g. {@code DPD_7_SMS_REPRESENT}
 * @param dpdThreshold the DPD at or above which this step fires
 * @param actionType what the step does
 * @param channel the primary notification channel (SMS/EMAIL), or NONE for internal milestones
 * @param templateId the notification template id (empty for internal milestones)
 */
public record DunningStep(
        String stepId,
        int dpdThreshold,
        DunningActionType actionType,
        String channel,
        String templateId) {

    public DunningStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(templateId, "templateId");
        if (dpdThreshold < 1) {
            throw new IllegalArgumentException("dpdThreshold must be >= 1");
        }
    }
}
