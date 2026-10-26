package com.lms.collections.adapters.out.temporal;

import com.lms.collections.adapters.out.temporal.DunningCommands.FiredStep;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-memory {@link DunningActivities} for the in-JVM workflow tests. {@code fireStep} enforces the
 * {@code (caseId, stepId)} uniqueness the real adapter gets from the DB, so the test proves
 * duplicate ticks and replays never repeat an action.
 */
final class FakeDunningActivities implements DunningActivities {

    final List<String> firedStepIds = new ArrayList<>();
    final Set<String> uniqueKeys = new HashSet<>();
    final List<String> escalations = new ArrayList<>();
    final List<String> promises = new ArrayList<>();
    final List<String> assignments = new ArrayList<>();
    String closedStatus;
    String closedReason;

    @Override
    public boolean fireStep(String caseId, String loanId, String loanNumber, FiredStep step) {
        boolean newlyFired = uniqueKeys.add(caseId + "/" + step.stepId());
        if (newlyFired) {
            firedStepIds.add(step.stepId());
        }
        return newlyFired;
    }

    @Override
    public void markEscalated(
            String caseId, String loanId, String loanNumber, String status, String reason) {
        escalations.add(status + ":" + reason);
    }

    @Override
    public void assignAgent(String caseId, String agent) {
        assignments.add(agent);
    }

    @Override
    public void recordPromise(
            String caseId, long promiseEpochDay, long amountMinor, String currency) {
        promises.add(promiseEpochDay + "/" + amountMinor);
    }

    @Override
    public void closeCase(
            String caseId, String loanId, String loanNumber, String status, String reason) {
        this.closedStatus = status;
        this.closedReason = reason;
    }
}
