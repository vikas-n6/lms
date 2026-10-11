package com.lms.schedule;

import com.lms.money.Amortization;
import com.lms.money.Money;
import com.lms.money.Rate;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Foreclosure-quote helper (docs/domain-rules.md "Interest and penal accrual"): outstanding
 * principal, plus interest accrued since the last paid due date at APR/365 per actual day, plus any
 * unpaid penal, plus the foreclosure charge (a configurable rate on the outstanding principal). The
 * actual/365 day-count and the charge-on-principal basis are a documented simplification (ADR
 * 0005).
 *
 * <p>Loan-account supplies the live balances in Phase 2; this is the pure money composition.
 */
public final class Foreclosure {

    /** Actual/365 day-count basis for accrued interest (domain-rules.md). */
    private static final int DAY_COUNT_BASIS = 365;

    private Foreclosure() {}

    public static Money quote(
            Money outstandingPrincipal,
            Rate annualRate,
            long daysSinceLastPaidDue,
            Money unpaidPenal,
            Rate foreclosureChargeRate) {
        Objects.requireNonNull(outstandingPrincipal, "outstandingPrincipal");
        Objects.requireNonNull(unpaidPenal, "unpaidPenal");
        Objects.requireNonNull(foreclosureChargeRate, "foreclosureChargeRate");
        Money accruedInterest =
                Amortization.simpleInterest(
                        outstandingPrincipal,
                        annualRate,
                        daysSinceLastPaidDue,
                        DAY_COUNT_BASIS,
                        RoundingMode.HALF_UP);
        Money charge = outstandingPrincipal.percentOf(foreclosureChargeRate, RoundingMode.HALF_UP);
        return outstandingPrincipal.add(accruedInterest).add(unpaidPenal).add(charge);
    }
}
