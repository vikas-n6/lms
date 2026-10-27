package com.lms.reporting.domain;

/**
 * DPD buckets for the portfolio distribution (docs/domain-rules.md "Loan state machine"): CURRENT
 * (0), DPD_1_30, DPD_31_60, DPD_61_90, NPA (91+). Loans at or beyond {@link #NPA} are
 * non-performing. The bucket is derived from the days-past-due carried on {@code loan.dpd-updated};
 * {@link #fromDpd} keeps that derivation in one place so a read row and a recomputation agree.
 */
public enum DpdBucketCategory {
    CURRENT,
    DPD_1_30,
    DPD_31_60,
    DPD_61_90,
    NPA;

    /** Whether a loan in this bucket counts as non-performing (NPA). */
    public boolean isNonPerforming() {
        return this == NPA;
    }

    public static DpdBucketCategory fromDpd(int dpd) {
        if (dpd <= 0) {
            return CURRENT;
        }
        if (dpd <= 30) {
            return DPD_1_30;
        }
        if (dpd <= 60) {
            return DPD_31_60;
        }
        if (dpd <= 90) {
            return DPD_61_90;
        }
        return NPA;
    }

    /**
     * Lenient parse of a bucket name carried on an event; unknown/blank falls back to {@code
     * CURRENT}.
     */
    public static DpdBucketCategory parse(String name) {
        if (name == null || name.isBlank()) {
            return CURRENT;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return CURRENT;
        }
    }
}
