package com.lms.customer.testsupport;

import com.lms.customer.application.port.out.CustomerRepository;
import com.lms.customer.domain.Customer;
import com.lms.customer.domain.CustomerId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link CustomerRepository} for unit tests. */
public final class FakeCustomerRepository implements CustomerRepository {

    public final Map<CustomerId, Customer> saved = new LinkedHashMap<>();

    @Override
    public void save(Customer customer) {
        saved.put(customer.id(), customer);
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return Optional.ofNullable(saved.get(id));
    }
}
