package com.lms.origination.adapters.in.web;

import com.lms.origination.application.OriginationExceptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps origination failures to RFC 9457 problem details (CLAUDE.md "Errors"). */
@RestControllerAdvice
public class OriginationExceptionHandler {

    @ExceptionHandler(OriginationExceptions.NotFound.class)
    public ProblemDetail onNotFound(OriginationExceptions.NotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Application not found", e.getMessage());
    }

    @ExceptionHandler(OriginationExceptions.SubmissionBlocked.class)
    public ProblemDetail onBlocked(OriginationExceptions.SubmissionBlocked e) {
        ProblemDetail p =
                problem(HttpStatus.UNPROCESSABLE_ENTITY, "Submission blocked", e.getMessage());
        p.setProperty("missing", e.missing());
        return p;
    }

    @ExceptionHandler(OriginationExceptions.AcceptanceRefused.class)
    public ProblemDetail onRefused(OriginationExceptions.AcceptanceRefused e) {
        return problem(HttpStatus.CONFLICT, "Acceptance refused", e.getMessage());
    }

    @ExceptionHandler(OriginationExceptions.ComplianceDeclined.class)
    public ProblemDetail onCompliance(OriginationExceptions.ComplianceDeclined e) {
        return problem(HttpStatus.FORBIDDEN, "Compliance declined", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail onInvalid(RuntimeException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid request", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatus(status);
        p.setTitle(title);
        p.setDetail(detail);
        return p;
    }
}
