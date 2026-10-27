package com.lms.reporting.application.port.out;

import com.lms.reporting.domain.LoanSnapshotRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-model store for the per-loan projection. {@code save} upserts the whole row. */
public interface LoanReadRepository {

    Optional<LoanSnapshotRow> find(UUID loanId);

    /** The application id captured on loan.created, used to mark the funnel's disbursed stage. */
    Optional<String> findApplicationId(UUID loanId);

    void save(LoanSnapshotRow row, String applicationId);

    List<LoanSnapshotRow> findAll();
}
