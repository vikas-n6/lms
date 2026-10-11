package com.lms.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

/** Worked examples for the reducing-balance schedule engine (docs/domain-rules.md). */
class ScheduleEngineTest {

    private static final Currency INR = Currency.getInstance("INR");
    private final ScheduleEngine engine = new ReducingBalanceScheduleEngine();

    @Test
    void scheduleFor50kOver12MonthsAt24Pct() {
        Money principal = Money.of(5_000_000L, INR);
        Schedule s =
                engine.generate(principal, Rate.percent(24), 12, LocalDate.of(2026, 1, 15), false);

        assertThat(s.emi()).isEqualTo(Money.of(472_798L, INR));
        assertThat(s.items()).hasSize(12);
        // Principal portions sum exactly to P (rule 5).
        Money principalSum = Money.zero(INR);
        for (ScheduleItem item : s.items()) {
            principalSum = principalSum.add(item.principal());
            assertThat(item.interest().isNegative()).isFalse();
            assertThat(item.outstandingAfter().isNegative()).isFalse();
        }
        assertThat(principalSum).isEqualTo(principal);
        // The schedule fully amortises (rule 3).
        assertThat(s.items().get(11).outstandingAfter()).isEqualTo(Money.zero(INR));
        // totalPayable = principal + totalInterest.
        assertThat(s.totalPayable()).isEqualTo(principal.add(s.totalInterest()));
    }

    @Test
    void dueDatesClampToMonthEnd() {
        // Rule 4: 31 Jan + 1 month = 28 Feb (2026 is not a leap year), + 2 months = 31 Mar.
        Schedule s =
                engine.generate(
                        Money.of(5_000_000L, INR),
                        Rate.percent(24),
                        3,
                        LocalDate.of(2026, 1, 31),
                        true);
        assertThat(s.items().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(s.items().get(1).dueDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(s.items().get(2).dueDate()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void quoteSplitsFeeGstAndNetDisbursal() {
        Product product = TestProducts.defaultMicroLoan();
        OfferQuote q =
                engine.quote(product, Money.of(5_000_000L, INR), 12, LocalDate.of(2026, 1, 15));

        assertThat(q.fee()).isEqualTo(Money.of(100_000L, INR)); // 2% of 50,000.00
        assertThat(q.gst()).isEqualTo(Money.of(18_000L, INR)); // 18% of the fee
        assertThat(q.netDisbursal()).isEqualTo(Money.of(4_882_000L, INR));
        // net + fee + gst == principal (keeps the disbursement ledger entry balanced).
        assertThat(q.netDisbursal().add(q.fee()).add(q.gst())).isEqualTo(Money.of(5_000_000L, INR));
        // Effective APR exceeds the nominal monthly-compounded figure because of the up-front fee.
        assertThat(q.effectiveAnnualRate().percentage().doubleValue()).isGreaterThan(26.82);
    }

    @Test
    void quoteRejectsUnsupportedPrincipalOrTenor() {
        Product product = TestProducts.defaultMicroLoan();
        assertThatThrownBy(
                        () ->
                                engine.quote(
                                        product,
                                        Money.of(5_000_001L, INR),
                                        12,
                                        LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class); // off-step
        assertThatThrownBy(
                        () ->
                                engine.quote(
                                        product,
                                        Money.of(5_000_000L, INR),
                                        7,
                                        LocalDate.of(2026, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class); // tenor not offered
    }

    @Test
    void zeroRateScheduleHasNoInterest() {
        Schedule s =
                engine.generate(
                        Money.of(1_200_000L, INR),
                        Rate.zero(),
                        12,
                        LocalDate.of(2026, 1, 15),
                        true);
        assertThat(s.totalInterest()).isEqualTo(Money.zero(INR));
        assertThat(s.emi()).isEqualTo(Money.of(100_000L, INR));
    }
}
