package com.lms.reporting.application.port.out;

import com.lms.reporting.domain.ReconStatus;
import java.util.Optional;

/** Append-only reconciliation-status store. {@code insert} is idempotent on the run id. */
public interface ReconStatusRepository {

    void insert(ReconStatus status);

    /** The most recent run of the given type (TRIAL_BALANCE | BANK_RAIL). */
    Optional<ReconStatus> latest(String reconType);
}
