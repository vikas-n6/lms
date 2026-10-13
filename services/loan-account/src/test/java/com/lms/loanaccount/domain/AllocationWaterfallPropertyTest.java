package com.lms.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.util.ArrayList;
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
 * Property tests for the allocation waterfall (docs/domain-rules.md "Repayment allocation
 * waterfall"): over random dues and payments the key invariants always hold — nothing is lost or
 * invented, no component is over-allocated, and the result is deterministic.
 */
class AllocationWaterfallPropertyTest {

    @Provide
    Arbitrary<Currency> currencies() {
        return Arbitraries.of(
                Currency.getInstance("INR"),
                Currency.getInstance("JPY"),
                Currency.getInstance("KWD"));
    }

    private List<InstalmentDue> dues(long[][] rows, Currency ccy) {
        List<InstalmentDue> dues = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            dues.add(
                    new InstalmentDue(
                            i + 1,
                            Money.of(rows[i][0], ccy),
                            Money.of(rows[i][1], ccy),
                            Money.of(rows[i][2], ccy)));
        }
        return dues;
    }

    @Property(tries = 5_000)
    void allocatedPlusAdvanceAlwaysEqualsPayment(
            @ForAll @Size(min = 1, max = 6) List<@IntRange(min = 0, max = 9) Integer> ignoredShape,
            @ForAll @LongRange(min = 0, max = 200_000) long payment,
            @ForAll @LongRange(min = 0, max = 50_000) long seed,
            @ForAll("currencies") Currency ccy) {
        List<InstalmentDue> dues = randomDues(ignoredShape.size(), seed, ccy);
        Money pay = Money.of(payment, ccy);

        AllocationResult result = AllocationWaterfall.allocate(dues, pay);

        assertThat(result.allocatedTotal().add(result.advance())).isEqualTo(pay);
    }

    @Property(tries = 5_000)
    void noComponentIsOverAllocated(
            @ForAll @Size(min = 1, max = 6) List<@IntRange(min = 0, max = 9) Integer> shape,
            @ForAll @LongRange(min = 0, max = 200_000) long payment,
            @ForAll @LongRange(min = 0, max = 50_000) long seed,
            @ForAll("currencies") Currency ccy) {
        List<InstalmentDue> dues = randomDues(shape.size(), seed, ccy);
        Money pay = Money.of(payment, ccy);

        AllocationResult result = AllocationWaterfall.allocate(dues, pay);

        for (InstalmentDue due : dues) {
            for (Component component : Component.values()) {
                long allocated =
                        result.allocations().stream()
                                .filter(
                                        a ->
                                                a.instalmentNumber() == due.instalmentNumber()
                                                        && a.component() == component)
                                .mapToLong(a -> a.amount().minorUnits())
                                .sum();
                assertThat(allocated).isLessThanOrEqualTo(due.outstanding(component).minorUnits());
            }
        }
        // Allocated never exceeds the sum of all dues, and the advance is only the surplus beyond
        // it.
        long totalDue =
                dues.stream()
                        .mapToLong(
                                d ->
                                        d.penalOutstanding().minorUnits()
                                                + d.interestOutstanding().minorUnits()
                                                + d.principalOutstanding().minorUnits())
                        .sum();
        assertThat(result.allocatedTotal().minorUnits()).isEqualTo(Math.min(payment, totalDue));
    }

    @Property(tries = 2_000)
    void isDeterministic(
            @ForAll @Size(min = 1, max = 6) List<@IntRange(min = 0, max = 9) Integer> shape,
            @ForAll @LongRange(min = 0, max = 200_000) long payment,
            @ForAll @LongRange(min = 0, max = 50_000) long seed,
            @ForAll("currencies") Currency ccy) {
        List<InstalmentDue> dues = randomDues(shape.size(), seed, ccy);
        Money pay = Money.of(payment, ccy);

        AllocationResult a = AllocationWaterfall.allocate(dues, pay);
        AllocationResult b = AllocationWaterfall.allocate(dues, pay);

        assertThat(a).isEqualTo(b);
    }

    /** Deterministic pseudo-random dues from a seed, so the property can replay the same inputs. */
    private List<InstalmentDue> randomDues(int count, long seed, Currency ccy) {
        long[][] rows = new long[count][3];
        long x = seed + 1;
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < 3; j++) {
                x = (x * 6364136223846793005L + 1442695040888963407L);
                rows[i][j] = Math.floorMod(x >>> 16, 20_001L); // 0..20000 minor units
            }
        }
        return dues(rows, ccy);
    }
}
