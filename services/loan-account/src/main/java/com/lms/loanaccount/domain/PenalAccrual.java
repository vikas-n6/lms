package com.lms.loanaccount.domain;

import com.lms.money.Amortization;
import com.lms.money.Money;
import com.lms.money.Rate;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Daily penal accrual (docs/domain-rules.md "Interest and penal accrual"): after the grace period,
 * penal interest accrues each day on the overdue principal plus overdue interest at the product's
 * penal APR, simple (no compounding), actual/365 — {@code round(overdue × penalAPR / 365)}. Reuses
 * {@link Amortization#simpleInterest} so the day-count basis lives in one place (ADR 0005).
 */
public final class PenalAccrual {

    /** Actual/365 basis, as for the foreclosure quote (ADR 0005). */
    private static final int DAY_COUNT_BASIS = 365;

    private PenalAccrual() {}

    /** Penal accrued for a single day on {@code overdueBase} at {@code penalAnnualRate}. */
    public static Money forDay(Money overdueBase, Rate penalAnnualRate) {
        Objects.requireNonNull(overdueBase, "overdueBase");
        Objects.requireNonNull(penalAnnualRate, "penalAnnualRate");
        if (overdueBase.isNegative()) {
            throw new IllegalArgumentException("overdue base must be non-negative");
        }
        return Amortization.simpleInterest(
                overdueBase, penalAnnualRate, 1, DAY_COUNT_BASIS, RoundingMode.HALF_UP);
    }
}
