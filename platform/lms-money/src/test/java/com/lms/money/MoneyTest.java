package com.lms.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.RoundingMode;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency KWD = Currency.getInstance("KWD");

    @Test
    void addAndSubtractStayWithinOneCurrency() {
        Money a = Money.of(500_000, INR); // 5000.00
        Money b = Money.of(250_000, INR); // 2500.00
        assertThat(a.add(b)).isEqualTo(Money.of(750_000, INR));
        assertThat(a.subtract(b)).isEqualTo(Money.of(250_000, INR));
    }

    @Test
    void negateAbsAndSignPredicates() {
        Money positive = Money.of(100, INR);
        Money negative = Money.of(-100, INR);
        assertThat(positive.negate()).isEqualTo(negative);
        assertThat(negative.abs()).isEqualTo(positive);
        assertThat(positive.abs()).isEqualTo(positive);
        assertThat(positive.isPositive()).isTrue();
        assertThat(negative.isNegative()).isTrue();
        assertThat(Money.zero(INR).isZero()).isTrue();
    }

    @Test
    void addOverflowIsRejected() {
        Money max = Money.of(Long.MAX_VALUE, INR);
        assertThatThrownBy(() -> max.add(Money.of(1, INR))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void negateOverflowIsRejected() {
        assertThatThrownBy(() -> Money.of(Long.MIN_VALUE, INR).negate())
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void crossCurrencyArithmeticThrows() {
        Money inr = Money.of(100, INR);
        Money jpy = Money.of(100, JPY);
        assertThatThrownBy(() -> inr.add(jpy)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> inr.subtract(jpy)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> inr.compareTo(jpy)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void compareToOrdersByMinorUnits() {
        assertThat(Money.of(100, INR)).isGreaterThan(Money.of(50, INR));
        assertThat(Money.of(50, INR)).isLessThan(Money.of(100, INR));
        assertThat(Money.of(100, INR)).isEqualByComparingTo(Money.of(100, INR));
    }

    @Test
    void processingFeeIsTwoPercentOfPrincipal() {
        // 2% of 50,000 INR principal = 1,000 INR (domain-rules.md "The one product")
        Money principal = Money.ofMajor("50000", INR);
        Money fee = principal.percentOf(Rate.percent(2));
        assertThat(fee).isEqualTo(Money.ofMajor("1000", INR));
    }

    @Test
    void gstIsEighteenPercentOfFee() {
        // 18% GST on a 1,000 INR fee = 180 INR
        Money fee = Money.ofMajor("1000", INR);
        Money gst = fee.percentOf(Rate.percent(18));
        assertThat(gst).isEqualTo(Money.ofMajor("180", INR));
    }

    @Test
    void percentOfRoundsHalfUpByDefault() {
        // 1.5% of 101 minor units = 1.515 -> HALF_UP -> 2
        Money amount = Money.of(101, INR);
        assertThat(amount.percentOf(Rate.percent("1.5"))).isEqualTo(Money.of(2, INR));
        assertThat(amount.percentOf(Rate.percent("1.5"), RoundingMode.DOWN))
                .isEqualTo(Money.of(1, INR));
    }

    @Test
    void allocateUsesLargestRemainder() {
        // 100 minor units split 1:1:1 -> 34, 33, 33 (first part takes the extra unit)
        Money[] parts = Money.of(100, INR).allocate(1, 1, 1);
        assertThat(parts).containsExactly(Money.of(34, INR), Money.of(33, INR), Money.of(33, INR));
    }

    @Test
    void allocateByUnequalWeights() {
        // 100 split 2:1 -> 67, 33 (remainders 0.66.., 0.33.. -> extra unit to the larger)
        Money[] parts = Money.of(100, INR).allocate(2, 1);
        assertThat(parts).containsExactly(Money.of(67, INR), Money.of(33, INR));
    }

    @Test
    void allocateRejectsBadWeights() {
        Money amount = Money.of(100, INR);
        assertThatThrownBy(amount::allocate).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> amount.allocate(1, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> amount.allocate(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void majorUnitRoundTripRespectsCurrencyScale() {
        // JPY has 0 fraction digits, KWD has 3, INR has 2 -- scale is never hard-coded.
        assertThat(Money.ofMajor("1500", JPY)).isEqualTo(Money.of(1500, JPY));
        assertThat(Money.of(1500, JPY).toMajorString()).isEqualTo("1500");

        assertThat(Money.ofMajor("1.250", KWD)).isEqualTo(Money.of(1250, KWD));
        assertThat(Money.of(1250, KWD).toMajorString()).isEqualTo("1.250");

        assertThat(Money.ofMajor("5000.00", INR)).isEqualTo(Money.of(500_000, INR));
        assertThat(Money.of(500_000, INR).toMajorString()).isEqualTo("5000.00");
    }

    @Test
    void ofMajorRejectsSubMinorPrecision() {
        // JPY cannot carry a fractional part at all.
        assertThatThrownBy(() -> Money.ofMajor("10.5", JPY))
                .isInstanceOf(IllegalArgumentException.class);
        // INR cannot carry a third decimal place.
        assertThatThrownBy(() -> Money.ofMajor("10.005", INR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringCarriesCurrencyCode() {
        assertThat(Money.of(500_000, INR)).hasToString("INR 5000.00");
    }
}
