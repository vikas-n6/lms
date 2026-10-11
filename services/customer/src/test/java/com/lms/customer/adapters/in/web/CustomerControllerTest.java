package com.lms.customer.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.customer.application.CustomerNotFoundException;
import com.lms.customer.application.GetCustomerService;
import com.lms.customer.application.RegisterCustomerService;
import com.lms.customer.domain.Customer;
import com.lms.customer.domain.CustomerId;
import com.lms.customer.testsupport.FakeCustomerEventPublisher;
import com.lms.customer.testsupport.FakeCustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CustomerControllerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC);
    private final FakeCustomerRepository repository = new FakeCustomerRepository();
    private final CustomerController controller =
            new CustomerController(
                    new RegisterCustomerService(
                            clock, repository, new FakeCustomerEventPublisher()),
                    new GetCustomerService(repository));

    @Test
    void registerReturns201WithNonPiiBody() {
        ResponseEntity<CustomerResponse> response =
                controller.register(
                        new RegisterCustomerRequest(
                                "Asha Rao", "9876543210", LocalDate.of(1990, 1, 1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CustomerResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.displayName()).isEqualTo("Asha");
        assertThat(body.status()).isEqualTo("REGISTERED");
        assertThat(response.getHeaders().getLocation()).hasToString("/customers/" + body.id());
    }

    @Test
    void getReturnsAnExistingCustomer() {
        Customer customer =
                Customer.register(clock, "Asha Rao", "9876543210", LocalDate.of(1990, 1, 1));
        repository.save(customer);
        assertThat(controller.get(customer.id().toString()).displayName()).isEqualTo("Asha");
    }

    @Test
    void getUnknownRaisesNotFound() {
        String unknownId = CustomerId.newId(clock).toString();
        assertThatThrownBy(() -> controller.get(unknownId))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
