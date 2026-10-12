package com.lms.customer.application.port.out;

import com.lms.customer.domain.Customer;
import com.lms.customer.domain.CustomerId;
import java.util.Optional;

/** Outbound persistence port for customers. Implemented by an adapter; never by the domain. */
public interface CustomerRepository {

    void save(Customer customer);

    Optional<Customer> findById(CustomerId id);
}
