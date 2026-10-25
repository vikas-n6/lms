package com.lms.repayment.application.port.out;

import com.lms.repayment.domain.Repayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for repayments (implemented by a JDBC adapter). No loan-DB access. */
public interface RepaymentRepository {

    void save(Repayment repayment);

    Optional<Repayment> findById(UUID id);

    Optional<Repayment> findByProviderReference(String providerReference);

    List<Repayment> findByLoanId(UUID loanId);
}
