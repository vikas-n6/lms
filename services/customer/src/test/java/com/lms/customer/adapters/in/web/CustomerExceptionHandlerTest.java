package com.lms.customer.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.customer.application.CustomerNotFoundException;
import com.lms.customer.domain.CustomerId;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class CustomerExceptionHandlerTest {

    private final CustomerExceptionHandler handler = new CustomerExceptionHandler();

    @Test
    void notFoundMapsTo404ProblemDetail() {
        ProblemDetail problem =
                handler.onNotFound(
                        new CustomerNotFoundException(CustomerId.newId(Clock.systemUTC())));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Customer not found");
    }

    @Test
    void illegalArgumentMapsTo422ProblemDetail() {
        ProblemDetail problem =
                handler.onInvalid(new IllegalArgumentException("phone must not be blank"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getDetail()).contains("phone");
    }
}
