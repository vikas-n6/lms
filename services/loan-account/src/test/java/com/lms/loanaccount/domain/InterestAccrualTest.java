package com.lms.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.schedule.ReducingBalanceScheduleEngine;
import com.lms.schedule.Schedule;
import com.lms.schedule.ScheduleItem;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Daily interest-income recognition (docs/domain-rules.md "Interest and penal accrual"). */
class InterestAccrualTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final LocalDate DISBURSED = LocalDate.of(2026, 1, 15);

    private Schedule schedule(long principalMinor, int tenor) {
        return new ReducingBalanceScheduleEngine()
                .generate(Money.of(principalMinor, INR), Rate.percent(24), tenor, DISBURSED, false);
    }

    @Test
    void recognisesNothingOnOrBeforeDisbursementOrAfterMaturity() {
        List<ScheduleItem> items = schedule(5_000_000L, 12).items();
        LocalDate maturity = items.get(items.size() - 1).dueDate();

        assertThat(InterestAccrual.recognise(items, DISBURSED, DISBURSED)).isEmpty();
        assertThat(InterestAccrual.recognise(items, DISBURSED, DISBURSED.minusDays(1))).isEmpty();
        assertThat(InterestAccrual.recognise(items, DISBURSED, maturity.plusDays(1))).isEmpty();
    }

    @Test
    void theLastDayOfAPeriodTakesTheRemainder() {
        List<ScheduleItem> items = schedule(5_000_000L, 12).items();
        ScheduleItem first = items.get(0);
        LocalDate firstDue = first.dueDate();
        int days = (int) ChronoUnit.DAYS.between(DISBURSED, firstDue);

        Money sum = Money.zero(INR);
        Money firstDayAmount = null;
        Money lastDayAmount = null;
        for (int d = 1; d <= days; d++) {
            LocalDate day = DISBURSED.plusDays(d);
            Money amount = InterestAccrual.recognise(items, DISBURSED, day).orElseThrow().amount();
            if (d == 1) {
                firstDayAmount = amount;
            }
            if (d == days) {
                lastDayAmount = amount;
            }
            sum = sum.add(amount);
        }

        assertThat(sum).isEqualTo(first.interest());
        // Even spread: the last day is >= an earlier day by at most the remainder.
        assertThat(lastDayAmount.minorUnits()).isGreaterThanOrEqualTo(firstDayAmount.minorUnits());
    }

    @Test
    void summingEveryDayReproducesScheduledInterestPerInstalmentAndInTotal() {
        for (int tenor : new int[] {3, 6, 9, 12}) {
            Schedule schedule = schedule(5_000_000L, tenor);
            List<ScheduleItem> items = schedule.items();
            LocalDate periodStart = DISBURSED;
            Money grandTotal = Money.zero(INR);

            for (ScheduleItem item : items) {
                LocalDate due = item.dueDate();
                Money perInstalment = Money.zero(INR);
                for (LocalDate day = periodStart.plusDays(1);
                        !day.isAfter(due);
                        day = day.plusDays(1)) {
                    Optional<DailyInterest> recognised =
                            InterestAccrual.recognise(items, DISBURSED, day);
                    assertThat(recognised).isPresent();
                    assertThat(recognised.get().instalmentNumber())
                            .isEqualTo(item.instalmentNumber());
                    perInstalment = perInstalment.add(recognised.get().amount());
                }
                // Exact to the minor unit (well inside the "1 minor unit/instalment" tolerance).
                assertThat(perInstalment).isEqualTo(item.interest());
                grandTotal = grandTotal.add(perInstalment);
                periodStart = due;
            }
            assertThat(grandTotal).isEqualTo(schedule.totalInterest());
        }
    }
}
