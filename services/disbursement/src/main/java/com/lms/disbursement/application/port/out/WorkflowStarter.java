package com.lms.disbursement.application.port.out;

import java.util.UUID;

/**
 * Starts and signals the payout workflow. Implemented by a Temporal adapter; the application stays
 * free of Temporal types. Starting is idempotent on the workflow id {@code disb-{loanId}} (a
 * duplicate start is a no-op).
 */
public interface WorkflowStarter {

    void startDisbursement(UUID disbursementId, UUID loanId, String loanNumber);

    void signalRailResult(UUID loanId, RailSignal signal);

    void signalAdminDecision(UUID loanId, boolean retry);

    /** Terminal rail result forwarded to the workflow after verification and de-duplication. */
    record RailSignal(boolean success, String railRef, String utr, String failureCode) {}
}
