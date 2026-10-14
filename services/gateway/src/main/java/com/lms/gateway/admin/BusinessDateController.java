package com.lms.gateway.admin;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only admin endpoint to set or advance the simulated business date (phase prompt task 1),
 * stored in the Redis key {@code lms:platform:business-date} that each service reads through its
 * {@code BusinessDateSource}. Admin-only at the edge; active only under the {@code dev} profile.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/admin/business-date")
public class BusinessDateController {

    static final String KEY = "lms:platform:business-date";

    private final StringRedisTemplate redis;

    public BusinessDateController(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @GetMapping
    public Map<String, Object> current() {
        String value = redis.opsForValue().get(KEY);
        return Map.of("businessDate", value == null ? "" : value, "overridden", value != null);
    }

    @PutMapping
    public Map<String, Object> set(@RequestBody SetBusinessDateRequest request) {
        redis.opsForValue().set(KEY, request.date().toString());
        return current();
    }

    @PostMapping("/advance")
    public Map<String, Object> advance(@RequestParam(defaultValue = "1") long days) {
        String value = redis.opsForValue().get(KEY);
        LocalDate base =
                value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value);
        redis.opsForValue().set(KEY, base.plusDays(days).toString());
        return current();
    }

    public record SetBusinessDateRequest(LocalDate date) {}
}
