package com.lms.decision.application.port.out;

/**
 * Sliding-window velocity counter keyed by the phone blind index (docs/architecture.md Redis
 * rules). The production adapter is a Redis sliding window implemented with a Lua script; tests use
 * an in-memory counter.
 */
public interface VelocityWindow {

    /**
     * Record this application in the window and return the count within the window, including this
     * one (so the Nth application sees N).
     */
    int recordAndCount(String phoneBlindIndex);
}
