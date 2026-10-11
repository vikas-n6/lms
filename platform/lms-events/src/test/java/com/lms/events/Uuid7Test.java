package com.lms.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Uuid7Test {

    @Test
    void hasVersion7AndRfcVariant() {
        UUID id = Uuid7.generate(Instant.now());
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2); // IETF variant (10xx)
    }

    @Test
    void laterTimestampsSortAfterEarlierOnes() {
        UUID earlier = Uuid7.generate(Instant.ofEpochMilli(1_000_000_000_000L));
        UUID later = Uuid7.generate(Instant.ofEpochMilli(1_000_000_060_000L));
        // Compare the 48-bit timestamp prefix (top of the most-significant bits).
        assertThat(timestampPrefix(later)).isGreaterThan(timestampPrefix(earlier));
    }

    @Test
    void usesTheInjectedClock() {
        Instant fixed = Instant.parse("2026-10-11T00:00:00Z");
        Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);
        UUID id = Uuid7.generate(clock);
        assertThat(timestampPrefix(id)).isEqualTo(fixed.toEpochMilli());
    }

    @Test
    void generatesDistinctValues() {
        UUID a = Uuid7.generate(Instant.now());
        UUID b = Uuid7.generate(Instant.now().plus(Duration.ofMillis(1)));
        assertThat(a).isNotEqualTo(b);
    }

    private static long timestampPrefix(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
