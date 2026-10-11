package com.lms.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

/**
 * Property tests for the money path (docs/domain-rules.md "Money type"). Each runs at least 10,000
 * generated cases and exercises currencies with 0 (JPY), 2 (INR) and 3 (KWD) fraction digits so the
 * minor-unit scale cannot silently be hard-coded.
 */
class MoneyPropertyTest {

    // Bounded so that a sum of three values can never overflow a long.
    private static final long BOUND = 3_000_000_000_000_000L;

    @Provide
    Arbitrary<Currency> currencies() {
        return Arbitraries.of(
                Currency.getInstance("INR"), // 2 digits
                Currency.getInstance("JPY"), // 0 digits
                Currency.getInstance("KWD"), // 3 digits
                Currency.getInstance("USD")); // 2 digits
    }

    @Property(tries = 10_000)
    void additionIsAssociative(
            @ForAll @LongRange(min = -BOUND, max = BOUND) long a,
            @ForAll @LongRange(min = -BOUND, max = BOUND) long b,
            @ForAll @LongRange(min = -BOUND, max = BOUND) long c,
            @ForAll("currencies") Currency currency) {
        Money ma = Money.of(a, currency);
        Money mb = Money.of(b, currency);
        Money mc = Money.of(c, currency);
        assertThat(ma.add(mb).add(mc)).isEqualTo(ma.add(mb.add(mc)));
    }

    @Property(tries = 10_000)
    void additionIsCommutative(
            @ForAll @LongRange(min = -BOUND, max = BOUND) long a,
            @ForAll @LongRange(min = -BOUND, max = BOUND) long b,
            @ForAll("currencies") Currency currency) {
        assertThat(Money.of(a, currency).add(Money.of(b, currency)))
                .isEqualTo(Money.of(b, currency).add(Money.of(a, currency)));
    }

    @Property(tries = 10_000)
    void subtractIsInverseOfAdd(
            @ForAll @LongRange(min = -BOUND, max = BOUND) long a,
            @ForAll @LongRange(min = -BOUND, max = BOUND) long b,
            @ForAll("currencies") Currency currency) {
        Money ma = Money.of(a, currency);
        Money mb = Money.of(b, currency);
        assertThat(ma.add(mb).subtract(mb)).isEqualTo(ma);
    }

    @Property(tries = 10_000)
    void allocateAlwaysSumsToTheInput(
            @ForAll @LongRange(min = -BOUND, max = BOUND) long amount,
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 1, max = 10_000) Integer> weights,
            @ForAll("currencies") Currency currency) {
        long[] w = weights.stream().mapToLong(Integer::longValue).toArray();
        Money[] parts = Money.of(amount, currency).allocate(w);

        long sum = Arrays.stream(parts).mapToLong(Money::minorUnits).sum();
        assertThat(sum).isEqualTo(amount);
        assertThat(parts).allSatisfy(p -> assertThat(p.currency()).isEqualTo(currency));
    }

    @Property(tries = 10_000)
    void minorUnitRepresentationRoundTripsLosslessly(
            @ForAll @LongRange(min = -BOUND, max = BOUND) long amount,
            @ForAll("currencies") Currency currency) {
        Money money = Money.of(amount, currency);
        // Round-trip through the major-unit string representation (scale driven by the currency).
        Money viaMajor = Money.ofMajor(money.toMajorString(), currency);
        assertThat(viaMajor).isEqualTo(money);
        // Round-trip through the raw minor-unit representation (as stored and serialized).
        assertThat(Money.of(money.minorUnits(), money.currency())).isEqualTo(money);
    }
}
