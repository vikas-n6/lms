package com.lms.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.customer.domain.Customer;
import com.lms.customer.domain.CustomerId;
import com.lms.customer.testsupport.FakeCustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GetCustomerServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC);
    private final FakeCustomerRepository repository = new FakeCustomerRepository();
    private final GetCustomerService service = new GetCustomerService(repository);

    @Test
    void returnsAnExistingCustomer() {
        Customer customer =
                Customer.register(clock, "Asha Rao", "9876543210", LocalDate.of(1990, 1, 1));
        repository.save(customer);
        assertThat(service.byId(customer.id())).isEqualTo(customer);
    }

    @Test
    void throwsWhenMissing() {
        assertThatThrownBy(() -> service.byId(CustomerId.newId(clock)))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
