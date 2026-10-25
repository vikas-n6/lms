package com.lms.repayment.adapters.in.web;

import com.lms.security.callback.InvalidCallbackSignatureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps repayment errors to RFC 9457 problem details. */
@RestControllerAdvice
public class RepaymentExceptionHandler {

    @ExceptionHandler(InvalidCallbackSignatureException.class)
    public ProblemDetail onInvalidSignature(InvalidCallbackSignatureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onBadRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
