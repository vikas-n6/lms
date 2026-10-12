package com.lms.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Validation and helpers of the product record. */
class FixedTenorReducingBalanceProductTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void supportsPrincipalChecksRangeStepAndCurrency() {
        Product p = TestProducts.defaultMicroLoan();
        assertThat(p.supportsPrincipal(Money.of(500_000L, INR))).isTrue(); // min, on step
        assertThat(p.supportsPrincipal(Money.of(5_000_000L, INR))).isTrue(); // max, on step
        assertThat(p.supportsPrincipal(Money.of(550_000L, INR))).isTrue(); // 5,500.00 on step
        assertThat(p.supportsPrincipal(Money.of(525_000L, INR))).isFalse(); // 5,250.00 off step
        assertThat(p.supportsPrincipal(Money.of(400_000L, INR))).isFalse(); // below min
        assertThat(p.supportsPrincipal(Money.of(6_000_000L, INR))).isFalse(); // above max
        assertThat(p.supportsPrincipal(Money.of(500_000L, USD))).isFalse(); // wrong currency
    }

    @Test
    void largestSupportedAtMostFloorsToStepWithinRange() {
        Product p = TestProducts.defaultMicroLoan();
        // 47,750.00 floors to 47,500.00 (on the 500.00 step).
        assertThat(p.largestSupportedAtMost(Money.of(4_775_000L, INR)))
                .contains(Money.of(4_750_000L, INR));
        // At/above max caps to 50,000.00.
        assertThat(p.largestSupportedAtMost(Money.of(9_000_000L, INR)))
                .contains(Money.of(5_000_000L, INR));
        // Exactly on a step is unchanged.
        assertThat(p.largestSupportedAtMost(Money.of(550_000L, INR)))
                .contains(Money.of(550_000L, INR));
        // Below the minimum: no offerable principal.
        assertThat(p.largestSupportedAtMost(Money.of(400_000L, INR))).isEmpty();
        // Wrong currency: empty.
        assertThat(p.largestSupportedAtMost(Money.of(1_000_000L, USD))).isEmpty();
    }

    @Test
    void monthlyRateIsAprOverTwelve() {
        Product p = TestProducts.defaultMicroLoan();
        assertThat(p.monthlyInterestRate().percentage().doubleValue()).isEqualTo(2.0);
    }

    @Test
    void rejectsCurrencyMismatchAndBadRanges() {
        assertThatThrownBy(
                        () ->
                                new FixedTenorReducingBalanceProduct(
                                        "X",
                                        1,
                                        INR,
                                        Money.of(1L, USD), // wrong currency
                                        Money.of(10L, INR),
                                        Money.of(1L, INR),
                                        List.of(3),
                                        Rate.percent(24),
                                        Rate.percent(2),
                                        Rate.percent(18),
                                        Rate.percent(36),
                                        3,
                                        Rate.zero()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                        () ->
                                new FixedTenorReducingBalanceProduct(
                                        "X",
                                        1,
                                        INR,
                                        Money.of(10L, INR),
                                        Money.of(1L, INR), // min > max
                                        Money.of(1L, INR),
                                        List.of(3),
                                        Rate.percent(24),
                                        Rate.percent(2),
                                        Rate.percent(18),
                                        Rate.percent(36),
                                        3,
                                        Rate.zero()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                        () ->
                                new FixedTenorReducingBalanceProduct(
                                        "X",
                                        1,
                                        INR,
                                        Money.of(1L, INR),
                                        Money.of(10L, INR),
                                        Money.of(1L, INR),
                                        List.of(), // no tenors
                                        Rate.percent(24),
                                        Rate.percent(2),
                                        Rate.percent(18),
                                        Rate.percent(36),
                                        3,
                                        Rate.zero()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
