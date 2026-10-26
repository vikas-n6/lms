package com.lms.loanaccount.application.port.out;

import com.lms.loanaccount.domain.DpdSnapshot;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the per-loan DPD snapshot (phase prompt task 1). The DPD job reads the prior
 * snapshot to stay idempotent per {@code (loanId, businessDate)} and to publish the extra {@code
 * loan.dpd-updated} when DPD returns to zero, then upserts the new position.
 */
public interface DpdSnapshotRepository {

    Optional<DpdSnapshot> find(UUID loanId);

    void upsert(DpdSnapshot snapshot);
}
