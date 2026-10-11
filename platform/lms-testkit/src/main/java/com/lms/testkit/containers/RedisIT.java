package com.lms.testkit.containers;

import org.testcontainers.containers.GenericContainer;

/**
 * Base for integration tests needing Redis. Redis has no dedicated Testcontainers module, so this
 * uses {@code GenericContainer}. Requires Docker; executed under Failsafe (deferred where Docker is
 * absent).
 */
public abstract class RedisIT {

    private static final int REDIS_PORT = 6379;

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8-alpine").withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    protected static String redisHost() {
        return REDIS.getHost();
    }

    protected static int redisPort() {
        return REDIS.getMappedPort(REDIS_PORT);
    }
}
