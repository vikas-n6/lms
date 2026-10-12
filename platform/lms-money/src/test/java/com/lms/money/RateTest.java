package com.lms.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.junit.jupiter.api.Test;

class RateTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void dividingAnAprGivesTheMonthlyRate() {
        // 24% APR / 12 = 2% monthly (domain-rules.md "Schedule engine")
        Rate monthly = Rate.percent(24).dividedBy(12);
        Money interest = Money.ofMajor("10000", INR).percentOf(monthly);
        assertThat(interest).isEqualTo(Money.ofMajor("200", INR));
    }

    @Test
    void zeroRateYieldsZeroMoney() {
        assertThat(Money.of(123_456, INR).percentOf(Rate.zero())).isEqualTo(Money.zero(INR));
    }

    @Test
    void dividingByZeroIsRejected() {
        assertThatThrownBy(() -> Rate.percent(24).dividedBy(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stringAndLongFactoriesAgree() {
        assertThat(Rate.percent("24")).isEqualTo(Rate.percent(24));
    }

    @Test
    void asPlainStringRendersThePercentage() {
        assertThat(Rate.percent("28.1234").asPlainString()).isEqualTo("28.1234");
    }
}
