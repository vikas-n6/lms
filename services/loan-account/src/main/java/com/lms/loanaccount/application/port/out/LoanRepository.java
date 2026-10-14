package com.lms.loanaccount.application.port.out;

import com.lms.loanaccount.domain.Loan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the loan aggregate. Lookups by application id back the idempotent
 * consumption of {@code application.accepted} (one loan per application, also a DB unique
 * constraint). {@code update} uses optimistic locking on {@link Loan#version()}.
 */
public interface LoanRepository {

    Optional<Loan> findById(UUID id);

    Optional<Loan> findByApplicationId(String applicationId);

    /** The next value of the human-facing loan-number sequence. */
    long nextLoanSequence();

    void insert(Loan loan);

    void update(Loan loan);

    List<Loan> findAll(Optional<String> stateCode, Optional<String> customerId);
}
