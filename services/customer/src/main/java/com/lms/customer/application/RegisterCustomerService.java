package com.lms.customer.application;

import com.lms.customer.application.port.out.CustomerEventPublisher;
import com.lms.customer.application.port.out.CustomerRepository;
import com.lms.customer.domain.Customer;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers a customer: build the aggregate from the injected {@link Clock}, persist it, and
 * publish {@code customer.registered}. The save and the outbox write share one transaction ({@link
 * Transactional}), so the event is never emitted without the customer being stored, and vice versa.
 */
@Service
public class RegisterCustomerService {

    private final Clock clock;
    private final CustomerRepository repository;
    private final CustomerEventPublisher events;

    public RegisterCustomerService(
            Clock clock, CustomerRepository repository, CustomerEventPublisher events) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Transactional
    public Customer register(RegisterCustomerCommand command) {
        Objects.requireNonNull(command, "command");
        Customer customer =
                Customer.register(
                        clock, command.fullName(), command.phone(), command.dateOfBirth());
        repository.save(customer);
        events.customerRegistered(customer);
        return customer;
    }
}
