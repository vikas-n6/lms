package com.lms.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Reducing-balance EMI maths (docs/domain-rules.md "Schedule engine"). This lives in {@code
 * lms-money} because it is the one place BigDecimal and the EMI power computation are allowed; the
 * schedule engine ({@code lms-schedule}) consumes only {@link Money}/{@link Rate} results from here
 * and never touches BigDecimal itself.
 *
 * <p>All computation uses {@link MathContext#DECIMAL128}; only the final figures are rounded to
 * whole minor units.
 */
public final class Amortization {

    private static final MathContext MC = MathContext.DECIMAL128;

    private Amortization() {}

    /**
     * The level EMI for a reducing-balance loan:
     *
     * <pre>EMI = P r (1+r)^n / ((1+r)^n - 1)</pre>
     *
     * where {@code r} is the per-period rate and {@code n} the instalment count. Rounded to whole
     * minor units with the given mode (HALF_UP per domain-rules). A zero rate degenerates to {@code
     * round(P / n)}.
     */
    public static Money emi(
            Money principal, Rate periodRate, int instalments, RoundingMode rounding) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(periodRate, "periodRate");
        Objects.requireNonNull(rounding, "rounding");
        if (instalments <= 0) {
            throw new IllegalArgumentException("instalments must be positive");
        }
        BigDecimal p = BigDecimal.valueOf(principal.minorUnits());
        BigDecimal r = periodRate.asFraction();
        BigDecimal emi;
        if (r.signum() == 0) {
            emi = p.divide(BigDecimal.valueOf(instalments), MC);
        } else {
            BigDecimal pow = BigDecimal.ONE.add(r, MC).pow(instalments, MC);
            BigDecimal numerator = p.multiply(r, MC).multiply(pow, MC);
            BigDecimal denominator = pow.subtract(BigDecimal.ONE, MC);
            emi = numerator.divide(denominator, MC);
        }
        return new Money(emi.setScale(0, rounding).longValueExact(), principal.currency());
    }

    /**
     * The effective annual rate implied by a loan's cash flows (docs/domain-rules.md "Schedule
     * engine" rule 7): the customer receives {@code netDisbursed} at time 0 and repays {@code
     * instalments} at the end of each subsequent period. Returns the annualised rate {@code
     * ((1+i)^periodsPerYear - 1)} where {@code i} is the per-period internal rate of return, found
     * by bisection. See ADR 0005 for method and tolerance.
     *
     * @param periodsPerYear 12 for monthly instalments
     */
    public static Rate effectiveAnnualRate(
            Money netDisbursed, List<Money> instalments, int periodsPerYear) {
        Objects.requireNonNull(netDisbursed, "netDisbursed");
        Objects.requireNonNull(instalments, "instalments");
        if (periodsPerYear <= 0) {
            throw new IllegalArgumentException("periodsPerYear must be positive");
        }
        if (instalments.isEmpty()) {
            throw new IllegalArgumentException("at least one instalment is required");
        }
        BigDecimal net = BigDecimal.valueOf(netDisbursed.minorUnits());
        if (net.signum() <= 0) {
            return Rate.zero();
        }
        BigDecimal[] flows = new BigDecimal[instalments.size()];
        for (int k = 0; k < instalments.size(); k++) {
            flows[k] = BigDecimal.valueOf(instalments.get(k).minorUnits());
        }
        // f(i) = sum(flow_k / (1+i)^k) - net, decreasing in i. f(0) = sum(flows) - net >= 0 for a
        // loan that charges interest; as i grows f -> -net < 0, so a unique root exists in [0, hi].
        if (presentValue(flows, BigDecimal.ZERO).compareTo(net) <= 0) {
            return Rate.zero(); // no interest (or a subsidised loan): effective rate is zero.
        }
        BigDecimal lo = BigDecimal.ZERO;
        BigDecimal hi = BigDecimal.ONE; // 100% per period; expand if somehow still positive.
        while (presentValue(flows, hi).subtract(net).signum() > 0) {
            hi = hi.multiply(BigDecimal.valueOf(2), MC);
        }
        for (int iter = 0; iter < 200; iter++) {
            BigDecimal mid = lo.add(hi, MC).divide(BigDecimal.valueOf(2), MC);
            BigDecimal f = presentValue(flows, mid).subtract(net, MC);
            if (f.signum() > 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        BigDecimal i = lo.add(hi, MC).divide(BigDecimal.valueOf(2), MC);
        BigDecimal annual =
                BigDecimal.ONE.add(i, MC).pow(periodsPerYear, MC).subtract(BigDecimal.ONE, MC);
        BigDecimal percentage = annual.movePointRight(2).setScale(4, RoundingMode.HALF_UP);
        return new Rate(percentage);
    }

    /**
     * Actual/{@code daysInYear} simple interest: {@code round(base * annualRate * days /
     * daysInYear)}. Used by penal accrual and the foreclosure quote (docs/domain-rules.md "Interest
     * and penal accrual"), where the day-count basis is 365.
     */
    public static Money simpleInterest(
            Money base, Rate annualRate, long days, int daysInYear, RoundingMode rounding) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(annualRate, "annualRate");
        Objects.requireNonNull(rounding, "rounding");
        if (days < 0) {
            throw new IllegalArgumentException("days must be non-negative");
        }
        if (daysInYear <= 0) {
            throw new IllegalArgumentException("daysInYear must be positive");
        }
        BigDecimal result =
                BigDecimal.valueOf(base.minorUnits())
                        .multiply(annualRate.asFraction(), MC)
                        .multiply(BigDecimal.valueOf(days), MC)
                        .divide(BigDecimal.valueOf(daysInYear), MC)
                        .setScale(0, rounding);
        return new Money(result.longValueExact(), base.currency());
    }

    private static BigDecimal presentValue(BigDecimal[] flows, BigDecimal i) {
        BigDecimal onePlusI = BigDecimal.ONE.add(i, MC);
        BigDecimal pv = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ONE;
        for (BigDecimal flow : flows) {
            discount = discount.divide(onePlusI, MC); // (1+i)^-(k+1)
            pv = pv.add(flow.multiply(discount, MC), MC);
        }
        return pv;
    }
}
