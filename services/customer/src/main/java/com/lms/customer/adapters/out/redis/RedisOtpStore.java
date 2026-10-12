package com.lms.customer.adapters.out.redis;

import com.lms.customer.application.port.out.OtpStore;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed OTP store with a TTL (docs/architecture.md Redis rules). Single-use: {@link
 * #consume} deletes on a match. Verified against Redis in the Docker session.
 */
@Component
public class RedisOtpStore implements OtpStore {

    private final StringRedisTemplate redis;

    public RedisOtpStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void store(String key, String code, Duration ttl) {
        redis.opsForValue().set(key, code, ttl);
    }

    @Override
    public boolean consume(String key, String code) {
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            return false;
        }
        redis.delete(key);
        return true;
    }
}
