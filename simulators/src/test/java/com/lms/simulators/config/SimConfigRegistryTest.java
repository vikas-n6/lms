package com.lms.simulators.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SimConfigRegistryTest {

    private final SimConfigRegistry registry = new SimConfigRegistry();

    @Test
    void defaultsToHealthyForEverySimulator() {
        for (String sim : SimConfigRegistry.SIMULATORS) {
            assertThat(registry.get(sim).failureRate()).isZero();
        }
    }

    @Test
    void applyPresetAndReset() {
        registry.applyPreset("bureau", SimulatorPreset.DOWN, 42L);
        assertThat(registry.get("bureau").failureRate()).isEqualTo(1.0);
        assertThat(registry.get("bureau").seed()).isEqualTo(42L);

        registry.resetAll();
        assertThat(registry.get("bureau").failureRate()).isZero();
    }

    @Test
    void putReseedsTheRng() {
        SimulatorConfig flaky = SimulatorPreset.FLAKY.toConfig(7L);
        registry.put("kyc", flaky);
        assertThat(registry.get("kyc")).isEqualTo(flaky);
        assertThat(registry.random("kyc")).isNotNull();
    }

    @Test
    void unknownSimulatorIsRejected() {
        assertThatThrownBy(() -> registry.get("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void presetFromStringIsCaseInsensitiveAndValidated() {
        assertThat(SimulatorPreset.fromString("flaky")).isEqualTo(SimulatorPreset.FLAKY);
        assertThatThrownBy(() -> SimulatorPreset.fromString("weird"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
