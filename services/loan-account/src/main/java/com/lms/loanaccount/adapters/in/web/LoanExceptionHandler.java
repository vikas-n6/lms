package com.lms.loanaccount.adapters.in.web;

import com.lms.loanaccount.adapters.out.persistence.OptimisticLockingException;
import com.lms.loanaccount.application.LoanNotFoundException;
import com.lms.loanaccount.application.LoanTransitionRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps loan-account failures to RFC 9457 problem details (CLAUDE.md "Errors"). */
@RestControllerAdvice
public class LoanExceptionHandler {

    @ExceptionHandler(LoanNotFoundException.class)
    public ProblemDetail onNotFound(LoanNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Loan not found");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler(LoanTransitionRejectedException.class)
    public ProblemDetail onRejected(LoanTransitionRejectedException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Loan transition rejected");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler(OptimisticLockingException.class)
    public ProblemDetail onConflict(OptimisticLockingException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Concurrent modification");
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

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail onIllegalState(IllegalStateException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Invalid loan state");
        problem.setDetail(e.getMessage());
        return problem;
    }
}
