package com.lms.disbursement.adapters.in.web;

import com.lms.disbursement.application.DisbursementNotFoundException;
import com.lms.security.callback.InvalidCallbackSignatureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps disbursement errors to RFC 9457 problem details. */
@RestControllerAdvice
public class DisbursementExceptionHandler {

    @ExceptionHandler(InvalidCallbackSignatureException.class)
    public ProblemDetail onInvalidSignature(InvalidCallbackSignatureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(DisbursementNotFoundException.class)
    public ProblemDetail onNotFound(DisbursementNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onBadRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
