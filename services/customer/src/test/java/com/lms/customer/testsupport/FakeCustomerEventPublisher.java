package com.lms.customer.testsupport;

import com.lms.customer.application.port.out.CustomerEventPublisher;
import com.lms.customer.domain.Customer;
import java.util.ArrayList;
import java.util.List;

/** Records published customers for unit tests. */
public final class FakeCustomerEventPublisher implements CustomerEventPublisher {

    public final List<Customer> published = new ArrayList<>();

    @Override
    public void customerRegistered(Customer customer) {
        published.add(customer);
    }
}
