package com.lms.gateway.ratelimit;

import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Fixed-window rate limiter backed by Redis, so the window is shared across gateway instances
 * (docs/architecture.md "Redis rules"). Keys are namespaced {@code lms:gateway:ratelimit:<key>} and
 * always carry a TTL.
 */
public final class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final int maxPerWindow;
    private final Duration window;

    public RedisRateLimiter(StringRedisTemplate redis, int maxPerWindow, Duration window) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.maxPerWindow = maxPerWindow;
        this.window = window;
    }

    @Override
    public boolean allow(String key) {
        String redisKey = "lms:gateway:ratelimit:" + key;
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redis.expire(redisKey, window);
        }
        return count == null || count <= maxPerWindow;
    }
}
