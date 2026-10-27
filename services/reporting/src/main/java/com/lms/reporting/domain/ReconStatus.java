package com.lms.reporting.domain;

import java.time.Instant;
import java.util.UUID;

/** Latest reconciliation outcome from ledger.recon-completed, for the trial-balance dashboard. */
public record ReconStatus(
        UUID reconRunId,
        String reconType,
        String currency,
        boolean balanced,
        long diffMinor,
        int matched,
        int missingInLedger,
        int missingInBank,
        int amountMismatch,
        int duplicate,
        Instant runAt) {}
