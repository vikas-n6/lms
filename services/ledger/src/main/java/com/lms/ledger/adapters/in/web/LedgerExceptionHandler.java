package com.lms.ledger.adapters.in.web;

import com.lms.ledger.application.EntryAlreadyReversedException;
import com.lms.ledger.application.EntryNotFoundException;
import com.lms.ledger.domain.UnbalancedEntryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps ledger failures to RFC 9457 problem details (CLAUDE.md "Errors"). */
@RestControllerAdvice
public class LedgerExceptionHandler {

    @ExceptionHandler(EntryNotFoundException.class)
    public ProblemDetail onNotFound(EntryNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Journal entry not found");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler(EntryAlreadyReversedException.class)
    public ProblemDetail onAlreadyReversed(EntryAlreadyReversedException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Entry already reversed");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler(UnbalancedEntryException.class)
    public ProblemDetail onUnbalanced(UnbalancedEntryException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Unbalanced journal entry");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail onInvalid(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Invalid request");
        problem.setDetail(e.getMessage());
        return problem;
    }
}
