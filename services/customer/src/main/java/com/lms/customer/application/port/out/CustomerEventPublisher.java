package com.lms.customer.application.port.out;

import com.lms.customer.domain.BankAccount;
import com.lms.customer.domain.Customer;
import com.lms.customer.domain.KycStatus;
import java.time.Instant;

/**
 * Outbound event port. The adapter writes to the transactional outbox in the same transaction as
 * the aggregate change (docs/architecture.md), so an event is never lost or sent without the write.
 * Publishes {@code customer.registered}, {@code customer.kyc.completed} and {@code
 * customer.bank-account.linked}.
 */
public interface CustomerEventPublisher {

    void customerRegistered(Customer customer);

    void kycCompleted(String customerId, KycStatus status, Instant completedAt);

    void bankAccountLinked(String customerId, BankAccount account);
}
