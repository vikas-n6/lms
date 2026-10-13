package com.lms.config;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Supplies an explicit business-date override when one is set (the dev Redis key {@code
 * lms:platform:business-date}), or empty to fall back to the clock. Implemented by an adapter where
 * the backing store (Redis) is available, keeping {@code lms-config} free of a Redis dependency
 * (hexagonal: the port lives here, the Redis adapter in the service).
 */
@FunctionalInterface
public interface BusinessDateSource {

    /** The overriding business date, or empty to use today in {@code Asia/Kolkata}. */
    Optional<LocalDate> currentOverride();

    /** A source that never overrides: the calendar then always reports the clock's date. */
    static BusinessDateSource none() {
        return Optional::empty;
    }
}
