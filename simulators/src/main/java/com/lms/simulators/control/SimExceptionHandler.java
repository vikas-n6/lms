package com.lms.simulators.control;

import com.lms.simulators.behavior.SimulatorUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps simulator errors to RFC 9457 problem details. */
@RestControllerAdvice
public class SimExceptionHandler {

    @ExceptionHandler(SimulatorUnavailableException.class)
    public ProblemDetail onUnavailable(SimulatorUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onBadRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
