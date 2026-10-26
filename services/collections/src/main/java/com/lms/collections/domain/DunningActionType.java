package com.lms.collections.domain;

/**
 * The kind of action a dunning step performs (phase prompt task 4). The channel-bearing types turn
 * into a notification; the others are internal milestones that change the case status. The concrete
 * steps and their DPD thresholds are versioned configuration ({@link DunningPlan}), never
 * hard-coded (CLAUDE.md global do-not list).
 */
public enum DunningActionType {
    /** SMS reminder. */
    SMS,
    /** SMS and email reminder. */
    SMS_EMAIL,
    /** SMS plus a mandate re-presentation request. */
    SMS_REPRESENT,
    /** Escalate the case to the agent queue. */
    ESCALATE,
    /** Formal notice. */
    FORMAL_NOTICE,
    /** Final notice. */
    FINAL_NOTICE,
    /** NPA review milestone. */
    NPA_REVIEW,
    /** Recommend write-off. */
    RECOMMEND_WRITE_OFF
}
