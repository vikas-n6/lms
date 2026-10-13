package com.lms.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

/** Daily penal accrual (docs/domain-rules.md "Interest and penal accrual"): actual/365, HALF_UP. */
class PenalAccrualTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void accruesOneDayAtPenalAprActual365() {
        // 36% of 10,000.00 (1,000,000 paise) for one day: 1_000_000 * 0.36 / 365 = 986.3 -> 986.
        Money penal = PenalAccrual.forDay(Money.of(1_000_000L, INR), Rate.percent(36));
        assertThat(penal).isEqualTo(Money.of(986L, INR));
    }

    @Test
    void zeroBaseAccruesNothing() {
        assertThat(PenalAccrual.forDay(Money.zero(INR), Rate.percent(36)))
                .isEqualTo(Money.zero(INR));
    }

    @Test
    void rejectsNegativeBase() {
        assertThatThrownBy(() -> PenalAccrual.forDay(Money.of(-1L, INR), Rate.percent(36)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
