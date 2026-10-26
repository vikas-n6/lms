package com.lms.collections.application.port.out;

import com.lms.collections.domain.CollectionCase;
import com.lms.collections.domain.DunningPlan;
import java.time.LocalDate;

/**
 * Starts and signals the dunning workflow (phase prompt task 4). The implementation is the Temporal
 * adapter; the workflow id {@code dun-{loanId}} makes a start idempotent and addresses signals.
 */
public interface DunningWorkflowClient {

    /** Start {@code dun-{loanId}} for a freshly opened case (no-op if already started). */
    void start(CollectionCase c, DunningPlan plan);

    void signalDpdTick(String loanId, int daysPastDue, String bucket, LocalDate businessDate);

    void signalPaymentReceived(String loanId, boolean overdueCleared);

    void signalPromiseToPay(
            String loanId, LocalDate promiseDate, long amountMinor, String currency);

    void signalEscalate(String loanId, String reason);

    void signalAssignAgent(String loanId, String agent);

    void signalWrittenOff(String loanId);
}
