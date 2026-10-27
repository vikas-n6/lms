package com.lms.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

class RatiosTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void halfIsFiftyPercent() {
        assertThat(Ratios.basisPoints(1, 2)).isEqualTo(5_000L);
    }

    @Test
    void wholeIsOneHundredPercent() {
        assertThat(Ratios.basisPoints(7, 7)).isEqualTo(10_000L);
    }

    @Test
    void zeroDenominatorIsZeroNotAnError() {
        assertThat(Ratios.basisPoints(5, 0)).isZero();
    }

    @Test
    void roundsDown() {
        // 1/3 = 3333.33.. bps -> floored to 3333.
        assertThat(Ratios.basisPoints(1, 3)).isEqualTo(3_333L);
    }

    @Property
    void ratioIsBoundedByZeroAndTenThousandWhenPartNotLargerThanWhole(
            @ForAll @LongRange(min = 0, max = 1_000_000_000L) long part,
            @ForAll @LongRange(min = 1, max = 1_000_000_000L) long whole) {
        long capped = Math.min(part, whole);
        long bps = Ratios.basisPoints(capped, whole);
        assertThat(bps).isBetween(0L, Ratios.ONE_HUNDRED_PERCENT_BPS);
    }
}
