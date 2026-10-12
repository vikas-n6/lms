package com.lms.decision.adapters.out.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.testkit.time.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryVelocityWindowTest {

    private final MutableClock clock = MutableClock.utc(Instant.parse("2026-10-12T00:00:00Z"));
    private final InMemoryVelocityWindow window = new InMemoryVelocityWindow(clock, 24);

    @Test
    void countsApplicationsWithinTheWindowInclusiveOfThisOne() {
        assertThat(window.recordAndCount("phone-a")).isEqualTo(1);
        assertThat(window.recordAndCount("phone-a")).isEqualTo(2);
        assertThat(window.recordAndCount("phone-a")).isEqualTo(3);
        assertThat(window.recordAndCount("phone-a")).isEqualTo(4); // the 4th breaches
    }

    @Test
    void separatesKeys() {
        window.recordAndCount("phone-a");
        assertThat(window.recordAndCount("phone-b")).isEqualTo(1);
    }

    @Test
    void evictsHitsOlderThanTheWindow() {
        window.recordAndCount("phone-a"); // t0
        window.recordAndCount("phone-a"); // t0 -> count 2
        clock.advance(Duration.ofHours(25)); // both now outside the 24h window
        assertThat(window.recordAndCount("phone-a")).isEqualTo(1);
    }
}
