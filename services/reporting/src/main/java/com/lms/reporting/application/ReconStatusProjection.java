package com.lms.reporting.application;

import com.lms.contracts.ledger.LedgerReconCompleted;
import com.lms.reporting.application.port.out.ReconStatusRepository;
import com.lms.reporting.domain.ReconStatus;
import java.util.Objects;
import java.util.UUID;

/** Projects ledger.recon-completed onto the reconciliation-status read model (ADR 0013). */
public class ReconStatusProjection {

    private final ReconStatusRepository reconStatus;

    public ReconStatusProjection(ReconStatusRepository reconStatus) {
        this.reconStatus = Objects.requireNonNull(reconStatus);
    }

    public void onReconCompleted(LedgerReconCompleted e) {
        reconStatus.insert(
                new ReconStatus(
                        UUID.fromString(e.getReconRunId()),
                        e.getReconType(),
                        e.getCurrency(),
                        e.getBalanced(),
                        e.getDiffMinorUnits(),
                        e.getMatched(),
                        e.getMissingInLedger(),
                        e.getMissingInBank(),
                        e.getAmountMismatch(),
                        e.getDuplicate(),
                        e.getRunAt()));
    }
}
