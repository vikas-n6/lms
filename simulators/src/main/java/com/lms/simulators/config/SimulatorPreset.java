package com.lms.simulators.config;

/** Named presets for the whole simulator estate (phase prompt S2). */
public enum SimulatorPreset {
    /** Fast and reliable. */
    HEALTHY,
    /** Slow but reliable. */
    SLOW,
    /** Intermittent latency, failures, timeouts and duplicate callbacks. */
    FLAKY,
    /** Every call fails. */
    DOWN;

    public SimulatorConfig toConfig(long seed) {
        return switch (this) {
            case HEALTHY -> new SimulatorConfig(5, 20, 0.0, 0.0, 0.0, 0, seed);
            case SLOW -> new SimulatorConfig(500, 2000, 0.0, 0.0, 0.0, 200, seed);
            case FLAKY -> new SimulatorConfig(20, 200, 0.30, 0.10, 0.25, 1000, seed);
            case DOWN -> new SimulatorConfig(0, 0, 1.0, 0.0, 0.0, 0, seed);
        };
    }

    public static SimulatorPreset fromString(String name) {
        for (SimulatorPreset preset : values()) {
            if (preset.name().equalsIgnoreCase(name)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("unknown preset: " + name);
    }
}
