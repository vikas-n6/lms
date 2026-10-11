package com.lms.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

/** Foreclosure-quote composition (docs/domain-rules.md "Interest and penal accrual"). */
class ForeclosureTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void quoteSumsPrincipalAccruedInterestPenalAndCharge() {
        // Outstanding 20,000.00, 24% APR for 15 days actual/365, 150.00 unpaid penal, 0% charge.
        Money outstanding = Money.of(2_000_000L, INR);
        Money unpaidPenal = Money.of(15_000L, INR);
        // accrued = 2,000,000 * 0.24 * 15 / 365 = 19,726.03 -> 19,726 paise.
        Money expectedAccrued = Money.of(19_726L, INR);

        Money quote =
                Foreclosure.quote(outstanding, Rate.percent(24), 15, unpaidPenal, Rate.zero());

        assertThat(quote).isEqualTo(outstanding.add(expectedAccrued).add(unpaidPenal));
    }

    @Test
    void quoteIncludesTheForeclosureChargeWhenConfigured() {
        Money outstanding = Money.of(2_000_000L, INR);
        Money noCharge =
                Foreclosure.quote(outstanding, Rate.percent(24), 0, Money.zero(INR), Rate.zero());
        Money withCharge =
                Foreclosure.quote(
                        outstanding, Rate.percent(24), 0, Money.zero(INR), Rate.percent(1));
        // 1% of 20,000.00 = 200.00 extra.
        assertThat(withCharge.subtract(noCharge)).isEqualTo(Money.of(20_000L, INR));
    }
}
