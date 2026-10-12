package com.lms.customer.testsupport;

import com.lms.customer.application.port.out.CustomerEventPublisher;
import com.lms.customer.domain.BankAccount;
import com.lms.customer.domain.Customer;
import com.lms.customer.domain.KycStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Records published customer events for unit tests. */
public final class FakeCustomerEventPublisher implements CustomerEventPublisher {

    public final List<Customer> published = new ArrayList<>();
    public final List<String> kycCompleted = new ArrayList<>();
    public final List<String> bankLinked = new ArrayList<>();

    @Override
    public void customerRegistered(Customer customer) {
        published.add(customer);
    }

    @Override
    public void kycCompleted(String customerId, KycStatus status, Instant completedAt) {
        kycCompleted.add(customerId + ":" + status);
    }

    @Override
    public void bankAccountLinked(String customerId, BankAccount account) {
        bankLinked.add(customerId + ":" + account.maskedNumber());
    }
}
