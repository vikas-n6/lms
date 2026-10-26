package com.lms.simulators.config;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Holds the per-simulator {@link SimulatorConfig} and a seeded {@link Random} so behaviour is
 * reproducible. In-memory only — never a system of record (docs/architecture.md Redis/Kafka rules
 * apply to real state, not these mocks).
 */
@Component
public class SimConfigRegistry {

    /** The simulators with a control surface. */
    public static final List<String> SIMULATORS =
            List.of(
                    "bureau",
                    "kyc",
                    "bank",
                    "sms",
                    "rail",
                    "mandate",
                    "payments",
                    "email",
                    "push",
                    "whatsapp");

    private final Map<String, SimulatorConfig> configs = new ConcurrentHashMap<>();
    private final Map<String, Random> randoms = new ConcurrentHashMap<>();

    public SimConfigRegistry() {
        resetAll();
    }

    public static boolean isKnown(String simulator) {
        return SIMULATORS.contains(simulator);
    }

    private void requireKnown(String simulator) {
        if (!isKnown(simulator)) {
            throw new IllegalArgumentException("unknown simulator: " + simulator);
        }
    }

    public SimulatorConfig get(String simulator) {
        requireKnown(simulator);
        return configs.get(simulator);
    }

    public Map<String, SimulatorConfig> all() {
        return Map.copyOf(configs);
    }

    public SimulatorConfig put(String simulator, SimulatorConfig config) {
        requireKnown(simulator);
        configs.put(simulator, config);
        randoms.put(simulator, new Random(config.seed()));
        return config;
    }

    public SimulatorConfig applyPreset(String simulator, SimulatorPreset preset, long seed) {
        return put(simulator, preset.toConfig(seed));
    }

    /** Apply a preset to every simulator (shared seed). */
    public void applyPresetToAll(SimulatorPreset preset, long seed) {
        for (String simulator : SIMULATORS) {
            put(simulator, preset.toConfig(seed));
        }
    }

    /** Reset every simulator to HEALTHY with a fixed seed. */
    public void resetAll() {
        applyPresetToAll(SimulatorPreset.HEALTHY, 1L);
    }

    /** The seeded RNG for a simulator; callers synchronize on it for reproducible sequences. */
    public Random random(String simulator) {
        requireKnown(simulator);
        return randoms.get(simulator);
    }
}
