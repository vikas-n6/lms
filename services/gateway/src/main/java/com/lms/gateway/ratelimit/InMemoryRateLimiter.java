package com.lms.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter for a single node / tests. The production limiter is Redis-backed so
 * the window is shared across gateway instances ({@link RedisRateLimiter}).
 */
public final class InMemoryRateLimiter implements RateLimiter {

    private final int maxPerWindow;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(int maxPerWindow, Duration window, Clock clock) {
        this.maxPerWindow = maxPerWindow;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    @Override
    public boolean allow(String key) {
        long now = clock.millis();
        Window window =
                windows.compute(
                        key,
                        (k, existing) -> {
                            if (existing == null || now - existing.start >= windowMillis) {
                                return new Window(now);
                            }
                            return existing;
                        });
        return window.count.incrementAndGet() <= maxPerWindow;
    }

    private static final class Window {
        private final long start;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long start) {
            this.start = start;
        }
    }
}
