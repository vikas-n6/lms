package com.lms.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class ScheduleItemStateTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final LocalDate DUE = LocalDate.of(2026, 2, 15);
    private static final int GRACE = 3;

    private static Money inr(long m) {
        return Money.of(m, INR);
    }

    private static ScheduleItemState state(long scheduled, long paid, LocalDate on) {
        return ScheduleItemState.of(inr(scheduled), inr(paid), DUE, on, GRACE);
    }

    @Test
    void upcomingBeforeDueWithNothingPaid() {
        assertThat(state(10_000, 0, DUE.minusDays(5))).isEqualTo(ScheduleItemState.UPCOMING);
    }

    @Test
    void dueOnOrAfterDueDateWithinGrace() {
        assertThat(state(10_000, 0, DUE)).isEqualTo(ScheduleItemState.DUE);
        assertThat(state(10_000, 0, DUE.plusDays(GRACE))).isEqualTo(ScheduleItemState.DUE);
    }

    @Test
    void overduePastGraceWhenStillOwing() {
        assertThat(state(10_000, 0, DUE.plusDays(GRACE + 1))).isEqualTo(ScheduleItemState.OVERDUE);
        assertThat(state(10_000, 4_000, DUE.plusDays(30))).isEqualTo(ScheduleItemState.OVERDUE);
    }

    @Test
    void partiallyPaidBeforeOverdue() {
        assertThat(state(10_000, 4_000, DUE)).isEqualTo(ScheduleItemState.PARTIALLY_PAID);
        assertThat(state(10_000, 4_000, DUE.minusDays(2)))
                .isEqualTo(ScheduleItemState.PARTIALLY_PAID);
    }

    @Test
    void paidWhenFullySettledRegardlessOfDate() {
        assertThat(state(10_000, 10_000, DUE.plusDays(100))).isEqualTo(ScheduleItemState.PAID);
        assertThat(state(10_000, 10_500, DUE.minusDays(5))).isEqualTo(ScheduleItemState.PAID);
    }
}
