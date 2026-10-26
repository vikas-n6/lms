package com.lms.ledger.application.port.out;

import com.lms.ledger.domain.recon.ReconItem;
import com.lms.ledger.domain.recon.ReconResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for bank/rail reconciliation runs and their items (phase prompt task 6). */
public interface ReconRunRepository {

    /** Persist a run and its items; returns the run id. */
    UUID saveRun(LocalDate reconDate, String currency, ReconResult result, Instant runAt);

    /** Mark a recon item resolved with a note (admin action). */
    void resolveItem(UUID itemId, String note);

    /** Runs, newest first (admin view). */
    List<Run> listRuns();

    /** Items for a run (admin view). */
    List<StoredItem> itemsForRun(UUID runId);

    Optional<StoredItem> findItem(UUID itemId);

    /** A persisted run summary. */
    record Run(
            UUID id,
            LocalDate reconDate,
            String currency,
            int matched,
            int missingInLedger,
            int missingInBank,
            int amountMismatch,
            int duplicate,
            Instant runAt) {}

    /** A persisted recon item with its resolution state. */
    record StoredItem(
            UUID id, UUID runId, ReconItem item, boolean resolved, String resolutionNote) {}
}
