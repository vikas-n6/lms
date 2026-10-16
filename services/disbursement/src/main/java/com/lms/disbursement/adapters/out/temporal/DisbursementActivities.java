package com.lms.disbursement.adapters.out.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Side-effecting steps the deterministic {@link DisbursementWorkflow} drives. Each is idempotent
 * (it reconciles against the persisted disbursement/attempt), so Temporal's at-least-once activity
 * execution — and the rail's own idempotency on the per-attempt key — never produce a second
 * payout. The implementation (adapters.out) delegates to the application services and ports.
 */
@ActivityInterface
public interface DisbursementActivities {

    /** Compliance gate before any payout (CLAUDE.md CompliancePolicy). */
    @ActivityMethod
    boolean complianceApproved(String disbursementId, String loanId);

    /** Record INITIATED and publish {@code disbursement.initiated} (once, idempotent). */
    @ActivityMethod
    void recordInitiated(String disbursementId);

    /**
     * Record a new payout attempt and return its idempotency key (tied to disbursement+attempt).
     */
    @ActivityMethod
    String startAttempt(String disbursementId, int attemptNumber);

    /**
     * Call the rail with the attempt's idempotency key. Returns {@code PENDING} when accepted (a
     * callback is awaited) or {@code UNKNOWN} when the response was lost — never a blind re-payout.
     */
    @ActivityMethod
    RailResult callRail(String disbursementId, String idempotencyKey);

    /**
     * Query the rail's payout status by idempotency key (used after a lost response or a timeout).
     */
    @ActivityMethod
    RailResult queryStatus(String idempotencyKey);

    /** Record the terminal outcome of an attempt in {@code payout_attempts}. */
    @ActivityMethod
    void finishAttempt(
            String disbursementId, int attemptNumber, String outcome, String failureCode);

    /** Record SUCCEEDED (UTR, disbursal date) and publish {@code disbursement.succeeded}. */
    @ActivityMethod
    void recordSucceeded(String disbursementId, String railRef, String utr);

    /**
     * Record FAILED and publish {@code disbursement.failed} (loan-account then cancels the loan).
     */
    @ActivityMethod
    void recordFailed(String disbursementId, String reason);

    /** Park the disbursement in MANUAL_REVIEW for an operator. */
    @ActivityMethod
    void recordManualReview(String disbursementId);
}
