package com.lms.disbursement.adapters.out.temporal;

import com.lms.disbursement.domain.DisbursementStatus;
import com.lms.disbursement.domain.RetryPolicy;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/**
 * Deterministic implementation of the payout saga. All side effects go through {@link
 * DisbursementActivities}; the only non-determinism (time, waiting) uses Temporal primitives
 * ({@code Workflow.sleep}, {@code Workflow.await}) so the history replays identically (proved by
 * the {@code WorkflowReplayer} test). Structural changes must be guarded with {@code
 * Workflow.getVersion}.
 *
 * <p>Flow: compliance gate → record INITIATED → attempt loop. Each attempt calls the rail with its
 * own idempotency key, then awaits the callback signal or, on timeout/lost response, polls status
 * by key (never a blind re-payout). A definite failure is retried up to the configured maximum; an
 * unresolvable result parks in MANUAL_REVIEW to await an admin retry/cancel.
 */
public final class DisbursementWorkflowImpl implements DisbursementWorkflow {

    private DisbursementActivities activities;
    private RailResult signalled;
    private AdminDecision adminDecision;
    private String status = DisbursementStatus.INITIATED.name();

    @Override
    public void run(StartDisbursementCommand command) {
        SagaTimeouts t = command.timeouts();
        this.activities =
                Workflow.newActivityStub(
                        DisbursementActivities.class,
                        ActivityOptions.newBuilder()
                                .setStartToCloseTimeout(
                                        Duration.ofSeconds(t.activityTimeoutSeconds()))
                                .build());
        String id = command.disbursementId();

        if (!activities.complianceApproved(id, command.loanId())) {
            activities.recordFailed(id, "COMPLIANCE_REJECTED");
            status = DisbursementStatus.FAILED.name();
            return;
        }
        activities.recordInitiated(id);

        RetryPolicy retry =
                new RetryPolicy(
                        t.maxAttempts(),
                        Duration.ofSeconds(t.retryBackoffSeconds()),
                        t.retryMultiplier());

        int attempt = 0;
        while (true) {
            attempt++;
            if (attempt > 1) {
                Workflow.sleep(retry.backoffBefore(attempt));
            }
            String key = activities.startAttempt(id, attempt);
            RailResult ack = activities.callRail(id, key);

            RailResult result;
            if (ack.kind() == ResultKind.UNKNOWN) {
                // Response lost: never re-pay — establish the outcome by querying status.
                result = pollStatus(key, t);
            } else {
                boolean gotSignal =
                        Workflow.await(
                                Duration.ofSeconds(t.payoutTimeoutSeconds()),
                                () -> signalled != null);
                result = gotSignal ? consumeSignal() : pollStatus(key, t);
            }

            switch (result.kind()) {
                case SUCCESS -> {
                    activities.finishAttempt(id, attempt, "SUCCESS", null);
                    activities.recordSucceeded(id, result.railRef(), result.utr());
                    status = DisbursementStatus.SUCCEEDED.name();
                    return;
                }
                case FAILED -> {
                    activities.finishAttempt(id, attempt, "FAILED", result.failureCode());
                    if (!retry.shouldRetry(attempt)) {
                        activities.recordFailed(
                                id,
                                result.failureCode() == null
                                        ? "RAIL_FAILED"
                                        : result.failureCode());
                        status = DisbursementStatus.FAILED.name();
                        return;
                    }
                    // otherwise loop for the next attempt
                }
                case UNKNOWN -> {
                    activities.recordManualReview(id);
                    status = DisbursementStatus.MANUAL_REVIEW.name();
                    Workflow.await(() -> adminDecision != null);
                    AdminDecision decision = adminDecision;
                    adminDecision = null;
                    if (decision == AdminDecision.CANCEL) {
                        activities.recordFailed(id, "CANCELLED_IN_REVIEW");
                        status = DisbursementStatus.FAILED.name();
                        return;
                    }
                    status = DisbursementStatus.INITIATED.name(); // retry: loop again
                }
                case PENDING -> throw new IllegalStateException("PENDING is not a terminal result");
            }
        }
    }

    private RailResult pollStatus(String key, SagaTimeouts t) {
        for (int round = 0; round < t.pollRounds(); round++) {
            RailResult r = activities.queryStatus(key);
            if (ResultKind.isTerminal(r.kind())) {
                return r;
            }
            if (round < t.pollRounds() - 1) {
                Workflow.sleep(Duration.ofSeconds(t.pollBackoffSeconds()));
            }
        }
        return RailResult.unknown();
    }

    private RailResult consumeSignal() {
        RailResult r = signalled;
        signalled = null;
        return r;
    }

    @Override
    public void onRailResult(RailResult result) {
        // The callback adapter only forwards a verified, deduped, terminal result.
        if (result != null && ResultKind.isTerminal(result.kind())) {
            signalled = result;
        }
    }

    @Override
    public void onAdminDecision(AdminDecision decision) {
        this.adminDecision = decision;
    }

    @Override
    public String currentStatus() {
        return status;
    }
}
