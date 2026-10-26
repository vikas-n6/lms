package com.lms.ledger.domain.recon;

/**
 * How a reconciliation line is classified (phase prompt task 6): {@code MATCHED} when the bank
 * statement / rail report line and the account-1001 posting agree on reference and amount, or one
 * of the four discrepancy types.
 */
public enum ReconClassification {
    MATCHED,
    MISSING_IN_LEDGER,
    MISSING_IN_BANK,
    AMOUNT_MISMATCH,
    DUPLICATE
}
