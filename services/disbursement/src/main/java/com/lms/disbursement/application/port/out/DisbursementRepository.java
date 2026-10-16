package com.lms.disbursement.application.port.out;

import com.lms.disbursement.domain.Disbursement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for the disbursement aggregate (implemented by a JDBC adapter). */
public interface DisbursementRepository {

    void save(Disbursement disbursement);

    Optional<Disbursement> findById(UUID id);

    Optional<Disbursement> findByLoanId(UUID loanId);

    List<Disbursement> findAll();
}
