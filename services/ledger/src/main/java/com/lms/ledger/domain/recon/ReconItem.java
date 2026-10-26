package com.lms.ledger.domain.recon;

import com.lms.money.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome for one reconciled reference (phase prompt task 6): its classification and the two
 * amounts being compared (either may be absent when the reference is missing on one side).
 *
 * @param reference the matched reference
 * @param classification how it was classified
 * @param externalAmount the bank/rail amount, if present
 * @param ledgerAmount the account-1001 posting amount, if present
 */
public record ReconItem(
        String reference,
        ReconClassification classification,
        Optional<Money> externalAmount,
        Optional<Money> ledgerAmount) {

    public ReconItem {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(externalAmount, "externalAmount");
        Objects.requireNonNull(ledgerAmount, "ledgerAmount");
    }
}
