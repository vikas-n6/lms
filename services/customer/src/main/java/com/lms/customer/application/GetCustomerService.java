package com.lms.customer.application;

import com.lms.customer.application.port.out.CustomerRepository;
import com.lms.customer.domain.Customer;
import com.lms.customer.domain.CustomerId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fetches a customer by id, or raises {@link CustomerNotFoundException}. */
@Service
public class GetCustomerService {

    private final CustomerRepository repository;

    public GetCustomerService(CustomerRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Transactional(readOnly = true)
    public Customer byId(CustomerId id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
