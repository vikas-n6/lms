package com.lms.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Currency;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

/**
 * {@link Money#spreadWithRemainderLast(int)}: equal whole-minor-unit parts with the last part
 * taking the remainder (used by daily interest-income recognition, docs/domain-rules.md).
 */
class MoneySpreadTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void spreadsEvenlyAndPutsTheRemainderOnTheLastPart() {
        Money[] parts = Money.of(1_003, INR).spreadWithRemainderLast(3);

        assertThat(parts)
                .containsExactly(Money.of(334, INR), Money.of(334, INR), Money.of(335, INR));
    }

    @Test
    void aSinglePartIsTheWholeAmount() {
        assertThat(Money.of(777, INR).spreadWithRemainderLast(1))
                .containsExactly(Money.of(777, INR));
    }

    @Test
    void rejectsNonPositivePartCount() {
        assertThatThrownBy(() -> Money.of(10, INR).spreadWithRemainderLast(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Provide
    Arbitrary<Currency> currencies() {
        return Arbitraries.of(
                Currency.getInstance("INR"),
                Currency.getInstance("JPY"),
                Currency.getInstance("KWD"));
    }

    @Property(tries = 10_000)
    void partsAlwaysSumBackToTheWhole(
            @ForAll @LongRange(min = -5_000_000L, max = 5_000_000L) long minor,
            @ForAll @IntRange(min = 1, max = 40) int parts,
            @ForAll("currencies") Currency ccy) {
        Money whole = Money.of(minor, ccy);

        Money[] split = whole.spreadWithRemainderLast(parts);

        assertThat(split).hasSize(parts);
        Money sum = Money.zero(ccy);
        for (Money part : split) {
            sum = sum.add(part);
        }
        assertThat(sum).isEqualTo(whole);
        // All but the last are equal (the floor); the last carries any remainder.
        long base = split[0].minorUnits();
        assertThat(Arrays.stream(split).limit(parts - 1).allMatch(m -> m.minorUnits() == base))
                .isTrue();
    }
}
