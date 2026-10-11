package com.lms.testkit.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MutableClockTest {

    @Test
    void advancesAndResetsDeterministically() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = MutableClock.utc(start);

        assertThat(clock.instant()).isEqualTo(start);
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);

        clock.advance(Duration.ofDays(1));
        assertThat(clock.instant()).isEqualTo(start.plus(Duration.ofDays(1)));

        clock.setInstant(start);
        assertThat(clock.instant()).isEqualTo(start);
    }

    @Test
    void withZoneKeepsTheInstantButChangesTheZone() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        MutableClock clock = MutableClock.at(start, ZoneOffset.UTC);

        assertThat(clock.withZone(kolkata).getZone()).isEqualTo(kolkata);
        assertThat(clock.withZone(kolkata).instant()).isEqualTo(start);
    }
}
