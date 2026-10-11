package com.lms.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.testkit.time.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    private final MutableClock clock = MutableClock.utc(Instant.parse("2026-10-11T00:00:00Z"));
    private final RateLimiter limiter = new InMemoryRateLimiter(3, Duration.ofMinutes(1), clock);

    @Test
    void allowsUpToTheLimitThenRejects() {
        assertThat(limiter.allow("ip-1")).isTrue();
        assertThat(limiter.allow("ip-1")).isTrue();
        assertThat(limiter.allow("ip-1")).isTrue();
        assertThat(limiter.allow("ip-1")).isFalse();
    }

    @Test
    void windowResetsAfterItElapses() {
        limiter.allow("ip-2");
        limiter.allow("ip-2");
        limiter.allow("ip-2");
        assertThat(limiter.allow("ip-2")).isFalse();

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertThat(limiter.allow("ip-2")).isTrue();
    }

    @Test
    void keysAreIndependent() {
        limiter.allow("a");
        limiter.allow("a");
        limiter.allow("a");
        assertThat(limiter.allow("a")).isFalse();
        assertThat(limiter.allow("b")).isTrue();
    }
}
