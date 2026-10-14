package com.lms.loanaccount.adapters.out.redis;

import com.lms.config.BusinessDateSource;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the dev business-date override from the Redis key {@code lms:platform:business-date} (an
 * ISO date), falling back to the clock when it is unset (docs/architecture.md). This is the Redis
 * adapter for the {@link BusinessDateSource} port defined in {@code lms-config}; the gateway sets
 * the key via a dev-only admin endpoint.
 */
@Component
public class RedisBusinessDateSource implements BusinessDateSource {

    static final String KEY = "lms:platform:business-date";
    private static final Logger log = LoggerFactory.getLogger(RedisBusinessDateSource.class);

    private final StringRedisTemplate redis;

    public RedisBusinessDateSource(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public Optional<LocalDate> currentOverride() {
        String value = redis.opsForValue().get(KEY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.trim()));
        } catch (DateTimeParseException e) {
            log.warn("Ignoring malformed business-date override '{}' in {}", value, KEY);
            return Optional.empty();
        }
    }
}
