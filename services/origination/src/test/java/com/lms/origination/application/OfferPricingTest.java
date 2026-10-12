package com.lms.origination.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.schedule.FixedTenorReducingBalanceProduct;
import com.lms.schedule.OfferQuote;
import com.lms.schedule.Product;
import com.lms.schedule.ReducingBalanceScheduleEngine;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfferPricingTest {

    private static final Currency INR = Currency.getInstance("INR");
    private final OfferPricing pricing = new OfferPricing(new ReducingBalanceScheduleEngine());

    private static Product product() {
        return new FixedTenorReducingBalanceProduct(
                "MICRO-V1",
                1,
                INR,
                Money.of(500_000L, INR),
                Money.of(5_000_000L, INR),
                Money.of(50_000L, INR),
                List.of(3, 6, 9, 12),
                Rate.percent(24),
                Rate.percent(2),
                Rate.percent(18),
                Rate.percent(36),
                3,
                Rate.zero());
    }

    @Test
    void pricesOneBalancedOptionPerTenor() {
        List<OfferQuote> quotes =
                pricing.price(product(), Money.of(5_000_000L, INR), LocalDate.of(2026, 2, 15))
                        .orElseThrow();

        assertThat(quotes).extracting(OfferQuote::tenor).containsExactly(3, 6, 9, 12);
        assertThat(quotes)
                .allSatisfy(
                        q -> {
                            assertThat(q.principal()).isEqualTo(Money.of(5_000_000L, INR));
                            assertThat(q.netDisbursal().add(q.fee()).add(q.gst()))
                                    .isEqualTo(Money.of(5_000_000L, INR));
                        });
    }

    @Test
    void snapsApprovedLimitDownToAStep() {
        List<OfferQuote> quotes =
                pricing.price(product(), Money.of(4_775_000L, INR), LocalDate.of(2026, 2, 15))
                        .orElseThrow();
        assertThat(quotes.get(0).principal()).isEqualTo(Money.of(4_750_000L, INR));
    }

    @Test
    void returnsEmptyWhenApprovedLimitIsBelowTheMinimum() {
        assertThat(pricing.price(product(), Money.of(400_000L, INR), LocalDate.of(2026, 2, 15)))
                .isEmpty();
    }
}
