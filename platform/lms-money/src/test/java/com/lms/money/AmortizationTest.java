package com.lms.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for reducing-balance EMI and effective-rate maths (docs/domain-rules.md). */
class AmortizationTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final MathContext MC = MathContext.DECIMAL128;

    @Test
    void emiMatchesTheClosedFormFor24PctOver12Months() {
        // P = 50,000.00 (5,000,000 paise), APR 24% -> monthly 2%, n = 12.
        Money principal = Money.of(5_000_000L, INR);
        Rate monthly = Rate.percent(24).dividedBy(12);

        Money emi = Amortization.emi(principal, monthly, 12, RoundingMode.HALF_UP);

        // Independent closed-form reference (BigDecimal is allowed inside lms-money tests).
        BigDecimal r = new BigDecimal("0.02");
        BigDecimal pow = BigDecimal.ONE.add(r).pow(12, MC);
        BigDecimal expected =
                BigDecimal.valueOf(5_000_000L)
                        .multiply(r, MC)
                        .multiply(pow, MC)
                        .divide(pow.subtract(BigDecimal.ONE), MC)
                        .setScale(0, RoundingMode.HALF_UP);
        assertThat(emi).isEqualTo(Money.of(expected.longValueExact(), INR));
        assertThat(emi.minorUnits()).isEqualTo(472_798L);
    }

    @Test
    void zeroRateSplitsPrincipalEvenly() {
        Money emi =
                Amortization.emi(Money.of(1_200_000L, INR), Rate.zero(), 12, RoundingMode.HALF_UP);
        assertThat(emi).isEqualTo(Money.of(100_000L, INR));
    }

    @Test
    void emiRejectsNonPositiveInstalments() {
        assertThatThrownBy(
                        () ->
                                Amortization.emi(
                                        Money.of(1L, INR), Rate.zero(), 0, RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void effectiveRateEqualsNominalWhenThereIsNoFee() {
        // A clean reducing-balance loan with no fee: the effective annual rate should round to the
        // nominal 24% effective-of-monthly figure (1.02^12 - 1 = 26.8241...%), not the flat 24%.
        Money principal = Money.of(5_000_000L, INR);
        Rate monthly = Rate.percent(24).dividedBy(12);
        Money emi = Amortization.emi(principal, monthly, 12, RoundingMode.HALF_UP);
        List<Money> flows = new ArrayList<>();
        for (int k = 0; k < 12; k++) {
            flows.add(emi);
        }

        Rate eff = Amortization.effectiveAnnualRate(principal, flows, 12);

        // (1.02)^12 - 1 = 0.268241794... -> 26.8242% (within rounding of the integer-EMI flows).
        assertThat(eff.percentage().doubleValue()).isBetween(26.7, 26.9);
    }

    @Test
    void effectiveRateRisesWhenAFeeIsDeductedUpFront() {
        // Same EMIs, but the customer only receives principal minus a 2% fee + 18% GST on the fee.
        Money principal = Money.of(5_000_000L, INR);
        Rate monthly = Rate.percent(24).dividedBy(12);
        Money emi = Amortization.emi(principal, monthly, 12, RoundingMode.HALF_UP);
        List<Money> flows = new ArrayList<>();
        for (int k = 0; k < 12; k++) {
            flows.add(emi);
        }
        Money fee = principal.percentOf(Rate.percent(2));
        Money gst = fee.percentOf(Rate.percent(18));
        Money net = principal.subtract(fee).subtract(gst);

        Rate withFee = Amortization.effectiveAnnualRate(net, flows, 12);
        Rate noFee = Amortization.effectiveAnnualRate(principal, flows, 12);

        assertThat(withFee.percentage()).isGreaterThan(noFee.percentage());
    }

    @Test
    void effectiveRateIsZeroWhenPaymentsDoNotExceedCashReceived() {
        Money principal = Money.of(1_200_000L, INR);
        List<Money> flows = List.of(Money.of(100_000L, INR), Money.of(100_000L, INR));
        // Only 200,000 repaid against 1,200,000 received: no positive IRR.
        assertThat(Amortization.effectiveAnnualRate(principal, flows, 12)).isEqualTo(Rate.zero());
    }

    @Test
    void simpleInterestFollowsActualByDaysInYear() {
        // 36% penal APR on 10,000.00 overdue for 10 days, actual/365:
        // 1,000,000 * 0.36 * 10 / 365 = 9863.01... -> 9863 paise HALF_UP.
        Money accrued =
                Amortization.simpleInterest(
                        Money.of(1_000_000L, INR), Rate.percent(36), 10, 365, RoundingMode.HALF_UP);
        assertThat(accrued).isEqualTo(Money.of(9_863L, INR));
    }

    @Test
    void simpleInterestIsZeroForZeroDays() {
        assertThat(
                        Amortization.simpleInterest(
                                Money.of(1_000_000L, INR),
                                Rate.percent(36),
                                0,
                                365,
                                RoundingMode.HALF_UP))
                .isEqualTo(Money.zero(INR));
    }

    @Test
    void simpleInterestRejectsBadArguments() {
        assertThatThrownBy(
                        () ->
                                Amortization.simpleInterest(
                                        Money.of(1L, INR),
                                        Rate.percent(1),
                                        -1,
                                        365,
                                        RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                Amortization.simpleInterest(
                                        Money.of(1L, INR),
                                        Rate.percent(1),
                                        1,
                                        0,
                                        RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
