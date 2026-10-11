package com.lms.customer.adapters.in.web;

import com.lms.customer.application.CustomerNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain/application failures to RFC 9457 problem details (CLAUDE.md "Errors"). */
@RestControllerAdvice
public class CustomerExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail onNotFound(CustomerNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Customer not found");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onInvalid(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Invalid request");
        problem.setDetail(e.getMessage());
        return problem;
    }
}
