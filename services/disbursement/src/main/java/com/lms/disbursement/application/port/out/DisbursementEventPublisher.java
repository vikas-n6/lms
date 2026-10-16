package com.lms.disbursement.application.port.out;

import com.lms.disbursement.domain.Disbursement;

/**
 * Publishes disbursement lifecycle events to the outbox (never straight to Kafka). Keyed by loanId
 * so events for one loan stay ordered (docs/architecture.md).
 */
public interface DisbursementEventPublisher {

    void initiated(Disbursement disbursement);

    void succeeded(Disbursement disbursement);

    void failed(Disbursement disbursement);
}
