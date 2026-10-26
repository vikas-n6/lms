package com.lms.collections.adapters.in.web;

import com.lms.collections.application.CollectionCaseNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps collections failures to RFC 9457 problem details (CLAUDE.md "Errors"). */
@RestControllerAdvice
public class CollectionsExceptionHandler {

    @ExceptionHandler(CollectionCaseNotFoundException.class)
    public ProblemDetail onNotFound(CollectionCaseNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Collection case not found");
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
