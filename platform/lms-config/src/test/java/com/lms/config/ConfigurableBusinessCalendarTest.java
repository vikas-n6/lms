package com.lms.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigurableBusinessCalendarTest {

    // 2026-10-12T20:00:00Z is 2026-10-13T01:30 in Asia/Kolkata (+05:30): the IST date is the 13th.
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-10-12T20:00:00Z"), ZoneOffset.UTC);

    @Test
    void fallsBackToTodayInIstWhenNoOverride() {
        BusinessCalendar calendar =
                new ConfigurableBusinessCalendar(BusinessDateSource.none(), FIXED);

        assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 10, 13));
    }

    @Test
    void usesTheOverrideWhenPresent() {
        LocalDate advanced = LocalDate.of(2026, 12, 25);
        BusinessCalendar calendar =
                new ConfigurableBusinessCalendar(() -> Optional.of(advanced), FIXED);

        assertThat(calendar.today()).isEqualTo(advanced);
    }

    @Test
    void readsTheSourceEachCall() {
        var holder = new java.util.concurrent.atomic.AtomicReference<Optional<LocalDate>>();
        holder.set(Optional.empty());
        BusinessCalendar calendar = new ConfigurableBusinessCalendar(holder::get, FIXED);

        assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 10, 13));

        holder.set(Optional.of(LocalDate.of(2027, 1, 1)));
        assertThat(calendar.today()).isEqualTo(LocalDate.of(2027, 1, 1));
    }
}
