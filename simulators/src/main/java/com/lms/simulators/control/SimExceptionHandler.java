package com.lms.simulators.control;

import com.lms.simulators.behavior.SimulatorUnavailableException;
import com.lms.simulators.rail.RailSimulator;
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

    /**
     * An operation that was recorded but whose response was lost maps to 504: ambiguous to the
     * caller, who must query status by idempotency key rather than retry blindly.
     */
    @ExceptionHandler(RailSimulator.ResponseLostException.class)
    public ProblemDetail onResponseLost(RailSimulator.ResponseLostException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onBadRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
