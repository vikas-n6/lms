package com.lms.disbursement.adapters.out.temporal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link DisbursementActivities} for the workflow tests: it scripts the rail behaviour
 * and records what the workflow drove, so the saga logic is exercised in {@code
 * TestWorkflowEnvironment} without a database, Kafka or a real rail.
 */
final class FakeDisbursementActivities implements DisbursementActivities {

    boolean compliance = true;
    final Deque<RailResult> callRailScript = new ArrayDeque<>();
    RailResult defaultCallRail = RailResult.pending("RAIL-1");
    RailResult queryStatusResult = RailResult.unknown();

    final AtomicInteger callRailCount = new AtomicInteger();
    final AtomicInteger startAttemptCount = new AtomicInteger();
    final AtomicInteger queryStatusCount = new AtomicInteger();
    final AtomicInteger initiatedCount = new AtomicInteger();
    final AtomicInteger manualReviewCount = new AtomicInteger();

    volatile int succeededCount;
    volatile int failedCount;
    volatile String succeededRailRef;
    volatile String succeededUtr;
    volatile String failReason;

    @Override
    public boolean complianceApproved(String disbursementId, String loanId) {
        return compliance;
    }

    @Override
    public void recordInitiated(String disbursementId) {
        initiatedCount.incrementAndGet();
    }

    @Override
    public String startAttempt(String disbursementId, int attemptNumber) {
        startAttemptCount.incrementAndGet();
        return "disb-" + disbursementId + "-attempt-" + attemptNumber;
    }

    @Override
    public RailResult callRail(String disbursementId, String idempotencyKey) {
        callRailCount.incrementAndGet();
        return callRailScript.isEmpty() ? defaultCallRail : callRailScript.poll();
    }

    @Override
    public RailResult queryStatus(String idempotencyKey) {
        queryStatusCount.incrementAndGet();
        return queryStatusResult;
    }

    @Override
    public void finishAttempt(
            String disbursementId, int attemptNumber, String outcome, String failureCode) {
        // no-op record
    }

    @Override
    public void recordSucceeded(String disbursementId, String railRef, String utr) {
        succeededCount++;
        succeededRailRef = railRef;
        succeededUtr = utr;
    }

    @Override
    public void recordFailed(String disbursementId, String reason) {
        failedCount++;
        failReason = reason;
    }

    @Override
    public void recordManualReview(String disbursementId) {
        manualReviewCount.incrementAndGet();
    }
}
