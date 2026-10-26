package com.lms.collections.adapters.out.temporal;

import com.lms.collections.adapters.out.temporal.DunningCommands.FiredStep;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Side-effecting steps of the dunning workflow (phase prompt task 4): all persistence and event
 * publishing live here so the workflow itself stays deterministic (ADR 0008/0011). Every method is
 * idempotent so Temporal's at-least-once activity execution never produces a duplicate action.
 */
@ActivityInterface
public interface DunningActivities {

    /**
     * Record the action for {@code (caseId, stepId)} and publish the matching collections event, if
     * it has not fired before. Returns {@code true} if newly fired, {@code false} if the unique
     * {@code (caseId, stepId)} row already existed (a replay or duplicate tick).
     */
    @ActivityMethod
    boolean fireStep(String caseId, String loanId, String loanNumber, FiredStep step);

    /** Set the case status and publish {@code collections.escalated} with the reason. */
    @ActivityMethod
    void markEscalated(
            String caseId, String loanId, String loanNumber, String status, String reason);

    /** Assign an agent to the case. */
    @ActivityMethod
    void assignAgent(String caseId, String agent);

    /** Record a promise to pay on the case. */
    @ActivityMethod
    void recordPromise(String caseId, long promiseEpochDay, long amountMinor, String currency);

    /** Close the case with a terminal status and publish {@code collections.case-closed}. */
    @ActivityMethod
    void closeCase(String caseId, String loanId, String loanNumber, String status, String reason);
}
