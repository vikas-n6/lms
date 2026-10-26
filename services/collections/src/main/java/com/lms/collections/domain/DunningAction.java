package com.lms.collections.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recorded dunning action (phase prompt task 4). A unique {@code (caseId, stepId)} makes every
 * action fire at most once, so replays and duplicate DPD ticks never repeat an action. Append-only.
 *
 * @param id row id
 * @param caseId the case this action belongs to
 * @param stepId the dunning step that fired
 * @param dpdThreshold the step's DPD threshold
 * @param actionType what was done
 * @param channel the notification channel (or NONE)
 * @param templateId the notification template
 * @param firedAt when it fired
 */
public record DunningAction(
        UUID id,
        UUID caseId,
        String stepId,
        int dpdThreshold,
        String actionType,
        String channel,
        String templateId,
        Instant firedAt) {

    public DunningAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(firedAt, "firedAt");
    }
}
