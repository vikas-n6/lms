package com.lms.customer.application.port.out;

import com.lms.customer.domain.Customer;

/**
 * Outbound event port. The adapter writes {@code customer.registered} to the transactional outbox
 * in the same transaction as the save (docs/architecture.md), so the event cannot be lost or sent
 * without the customer being persisted.
 */
public interface CustomerEventPublisher {

    void customerRegistered(Customer customer);
}
