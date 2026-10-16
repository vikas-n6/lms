package com.lms.disbursement.adapters.out.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * The durable payout saga (phase prompt S2). One workflow per disbursement, id {@code
 * disb-{loanId}} so a duplicate start is a no-op. It consumes the signed rail callback as a signal
 * and survives timeouts, duplicate/out-of-order callbacks and crashes (Temporal persists the
 * history).
 */
@WorkflowInterface
public interface DisbursementWorkflow {

    @WorkflowMethod
    void run(StartDisbursementCommand command);

    /** Delivered by the callback adapter once a terminal rail result is deduped and verified. */
    @SignalMethod
    void onRailResult(RailResult result);

    /** Delivered by an admin acting on a MANUAL_REVIEW disbursement. */
    @SignalMethod
    void onAdminDecision(AdminDecision decision);

    /** Current saga status for queries / the admin UI. */
    @QueryMethod
    String currentStatus();
}
