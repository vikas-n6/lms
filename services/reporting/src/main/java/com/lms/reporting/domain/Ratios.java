package com.lms.reporting.domain;

import com.lms.money.Money;

/**
 * Integer basis-point ratios for the read-model metrics. Ratios are expressed as basis points
 * (per-ten-thousand) computed with exact integer arithmetic — reporting carries no {@code
 * BigDecimal} or floating point (docs/architecture.md: rounding/BigDecimal live only in lms-money,
 * and these are counts/denormalised copies, not a money arithmetic path). A zero denominator yields
 * 0 bps rather than throwing, so an empty portfolio reports 0% cleanly.
 */
public final class Ratios {

    public static final long ONE_HUNDRED_PERCENT_BPS = 10_000L;

    private Ratios() {}

    /** {@code part/whole} in basis points, rounded down; 0 when {@code whole} is 0. */
    public static long basisPoints(long part, long whole) {
        if (whole == 0L) {
            return 0L;
        }
        return Math.multiplyExact(part, ONE_HUNDRED_PERCENT_BPS) / whole;
    }

    /** Money ratio in basis points; both amounts must share a currency (checked by the caller). */
    public static long basisPoints(Money part, Money whole) {
        return basisPoints(part.minorUnits(), whole.minorUnits());
    }
}
