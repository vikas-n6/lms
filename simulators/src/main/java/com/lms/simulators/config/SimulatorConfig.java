package com.lms.simulators.config;

/**
 * Tunable behaviour of one simulator (phase prompt S2 control API). Rates are probabilities in
 * [0,1]; a {@code seed} makes a run reproducible.
 */
public record SimulatorConfig(
        int latencyMsMin,
        int latencyMsMax,
        double failureRate,
        double timeoutRate,
        double duplicateCallbackRate,
        long callbackDelayMs,
        long seed) {

    public SimulatorConfig {
        if (latencyMsMin < 0 || latencyMsMax < latencyMsMin) {
            throw new IllegalArgumentException("require 0 <= latencyMsMin <= latencyMsMax");
        }
        requireFraction(failureRate, "failureRate");
        requireFraction(timeoutRate, "timeoutRate");
        requireFraction(duplicateCallbackRate, "duplicateCallbackRate");
        if (callbackDelayMs < 0) {
            throw new IllegalArgumentException("callbackDelayMs must be non-negative");
        }
    }

    private static void requireFraction(double value, String field) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be within [0,1]");
        }
    }

    public SimulatorConfig withSeed(long newSeed) {
        return new SimulatorConfig(
                latencyMsMin,
                latencyMsMax,
                failureRate,
                timeoutRate,
                duplicateCallbackRate,
                callbackDelayMs,
                newSeed);
    }
}
