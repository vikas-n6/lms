package com.lms.repayment.application.port.out;

import com.lms.money.Money;
import com.lms.repayment.domain.Mandate;
import com.lms.repayment.domain.Repayment;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Publishes repayment/mandate events to the outbox (never straight to Kafka). Keyed by loanId so
 * events for one loan stay ordered (docs/architecture.md).
 */
public interface RepaymentEventPublisher {

    void repaymentReceived(Repayment repayment);

    void repaymentReversed(Repayment repayment, String reason);

    void mandateRegistered(Mandate mandate);

    void mandateDebitFailed(
            UUID loanId,
            String mandateId,
            Money amount,
            LocalDate dueDate,
            String failureCode,
            LocalDate rePresentOn);
}
