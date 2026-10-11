package com.lms.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.time.LocalDate;
import java.util.Currency;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property tests for the schedule engine (docs/domain-rules.md "Schedule engine" rule 5), across
 * every principal (5,000.00..50,000.00 step 500.00), tenor (3/6/9/12) and rate (0%..60% APR).
 */
class SchedulePropertyTest {

    private static final Currency INR = Currency.getInstance("INR");
    private final ScheduleEngine engine = new ReducingBalanceScheduleEngine();

    @Provide
    Arbitrary<Integer> tenors() {
        return Arbitraries.of(3, 6, 9, 12);
    }

    @Property(tries = 5_000)
    void scheduleInvariantsHold(
            @ForAll @IntRange(min = 0, max = 90) int step, // principal = 5,000.00 + step*500.00
            @ForAll("tenors") int tenor,
            @ForAll @IntRange(min = 0, max = 60) int aprPercent) {
        Money principal = Money.of(500_000L + (long) step * 50_000L, INR);
        Rate apr = Rate.percent(aprPercent);

        Schedule s = engine.generate(principal, apr, tenor, LocalDate.of(2026, 1, 15), true);

        Money principalSum = Money.zero(INR);
        Money interestSum = Money.zero(INR);
        Money previousOutstanding = principal;
        for (ScheduleItem item : s.items()) {
            // Interest never negative; principal portion never negative.
            assertThat(item.interest().isNegative()).as("interest >= 0").isFalse();
            assertThat(item.principal().isNegative()).as("principal portion >= 0").isFalse();
            // Outstanding never negative and strictly non-increasing.
            assertThat(item.outstandingAfter().isNegative()).as("balance >= 0").isFalse();
            assertThat(item.outstandingAfter().compareTo(previousOutstanding))
                    .as("balance non-increasing")
                    .isLessThanOrEqualTo(0);
            // amount = principal + interest.
            assertThat(item.amount()).isEqualTo(item.principal().add(item.interest()));
            previousOutstanding = item.outstandingAfter();
            principalSum = principalSum.add(item.principal());
            interestSum = interestSum.add(item.interest());
        }
        // Principal portions sum exactly to P and the loan fully amortises.
        assertThat(principalSum).as("principal portions sum to P").isEqualTo(principal);
        assertThat(s.items().get(tenor - 1).outstandingAfter()).isEqualTo(Money.zero(INR));
        assertThat(s.totalInterest()).isEqualTo(interestSum);

        // Total interest within 1 minor unit per instalment of the closed-form figure (n*EMI - P).
        long closedForm = (long) tenor * s.emi().minorUnits() - principal.minorUnits();
        long actual = s.totalInterest().minorUnits();
        assertThat(Math.abs(actual - closedForm))
                .as("total interest within tenor minor units of closed form")
                .isLessThanOrEqualTo(tenor);
    }

    @Property(tries = 2_000)
    void quoteAlwaysBalancesNetFeeGst(
            @ForAll @IntRange(min = 0, max = 90) int step, @ForAll("tenors") int tenor) {
        Product product = TestProducts.defaultMicroLoan();
        Money principal = Money.of(500_000L + (long) step * 50_000L, INR);

        OfferQuote q = engine.quote(product, principal, tenor, LocalDate.of(2026, 1, 15));

        // The OfferQuote constructor also enforces this, but assert it explicitly.
        assertThat(q.netDisbursal().add(q.fee()).add(q.gst())).isEqualTo(principal);
        assertThat(q.emi()).isEqualTo(q.schedule().emi());
    }
}
