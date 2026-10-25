package com.lms.repayment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RePresentationPolicyTest {

    @Test
    void rePresentsTwoBusinessDaysLaterSkippingWeekends() {
        // Thursday 2026-11-12 + 2 business days -> Monday 2026-11-16 (skips Sat/Sun).
        LocalDate thursday = LocalDate.of(2026, 11, 12);
        assertThat(RePresentationPolicy.rePresentOn(thursday, 2, Set.of()))
                .isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void skipsConfiguredHolidays() {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        LocalDate holiday = LocalDate.of(2026, 11, 10); // Tuesday holiday
        assertThat(RePresentationPolicy.rePresentOn(monday, 2, Set.of(holiday)))
                .isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    void aDebitIsRepresentedAtMostOnce() {
        assertThat(RePresentationPolicy.alreadyRepresented(1)).isFalse();
        assertThat(RePresentationPolicy.alreadyRepresented(2)).isTrue();
    }
}
