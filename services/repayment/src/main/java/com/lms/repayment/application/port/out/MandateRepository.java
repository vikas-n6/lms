package com.lms.repayment.application.port.out;

import com.lms.repayment.domain.Mandate;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for mandates (implemented by a JDBC adapter). */
public interface MandateRepository {

    void save(Mandate mandate);

    Optional<Mandate> findById(UUID id);

    Optional<Mandate> findActiveByLoanId(UUID loanId);
}
