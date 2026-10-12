package com.lms.decision.adapters.in.web;

import com.lms.decision.application.DecisionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps decision failures to RFC 9457 problem details. */
@RestControllerAdvice
public class DecisionExceptionHandler {

    @ExceptionHandler(DecisionNotFoundException.class)
    public ProblemDetail onNotFound(DecisionNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Decision not found");
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
