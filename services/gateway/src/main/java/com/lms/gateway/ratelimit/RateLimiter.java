package com.lms.gateway.ratelimit;

/** Decides whether a request keyed by {@code key} is within its rate budget. */
public interface RateLimiter {

    boolean allow(String key);
}
