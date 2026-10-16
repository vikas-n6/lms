package com.lms.disbursement.adapters.out.temporal;

/**
 * Input to {@link DisbursementWorkflow}. Ids are strings (serialised into workflow history);
 * amounts are not here — the activities load them from the persisted disbursement so the workflow
 * history stays small and money never travels as a primitive.
 */
public record StartDisbursementCommand(
        String disbursementId, String loanId, String loanNumber, SagaTimeouts timeouts) {}
