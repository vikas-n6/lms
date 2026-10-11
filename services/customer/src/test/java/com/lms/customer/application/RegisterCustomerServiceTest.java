package com.lms.customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.customer.domain.Customer;
import com.lms.customer.testsupport.FakeCustomerEventPublisher;
import com.lms.customer.testsupport.FakeCustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RegisterCustomerServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC);
    private final FakeCustomerRepository repository = new FakeCustomerRepository();
    private final FakeCustomerEventPublisher events = new FakeCustomerEventPublisher();
    private final RegisterCustomerService service =
            new RegisterCustomerService(clock, repository, events);

    @Test
    void persistsAndPublishesExactlyOnce() {
        Customer customer =
                service.register(
                        new RegisterCustomerCommand(
                                "Asha Rao", "9876543210", LocalDate.of(1990, 1, 1)));

        assertThat(repository.saved).containsKey(customer.id());
        assertThat(events.published).containsExactly(customer);
        assertThat(customer.status().name()).isEqualTo("REGISTERED");
    }
}
