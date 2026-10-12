package com.lms.decision.adapters.out.velocity;

import com.lms.decision.application.port.out.VelocityWindow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single-instance sliding-window velocity counter (phase prompt S5 / domain-rules "Velocity").
 * Keeps per-key request timestamps and counts those within the window, evicting older ones against
 * the injected {@link Clock}. This is the dev/default implementation; the distributed production
 * version is a Redis sliding window with a Lua script (Docker session), which this port abstracts.
 */
@Component
public class InMemoryVelocityWindow implements VelocityWindow {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration window;

    public InMemoryVelocityWindow(
            Clock clock, @Value("${lms.decision.velocity.window-hours:24}") long windowHours) {
        this.clock = clock;
        this.window = Duration.ofHours(windowHours);
    }

    @Override
    public int recordAndCount(String key) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(now);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            return timestamps.size();
        }
    }
}
