package com.lms.simulators.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SimulatorConfigTest {

    @Test
    void rejectsInvalidRangesAndRates() {
        assertThatThrownBy(() -> new SimulatorConfig(10, 5, 0, 0, 0, 0, 0, 0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class); // max < min
        assertThatThrownBy(() -> new SimulatorConfig(0, 10, 1.5, 0, 0, 0, 0, 0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class); // failureRate > 1
        assertThatThrownBy(() -> new SimulatorConfig(0, 10, 0, 0, 0, 1.5, 0, 0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class); // unknownOutcomeRate > 1
        assertThatThrownBy(() -> new SimulatorConfig(0, 10, 0, 0, 0, 0, 0, 0, -1, 1))
                .isInstanceOf(IllegalArgumentException.class); // negative callback delay
    }

    @Test
    void withSeedReplacesOnlyTheSeed() {
        SimulatorConfig base = SimulatorPreset.FLAKY.toConfig(1L);
        SimulatorConfig reseeded = base.withSeed(99L);
        assertThat(reseeded.seed()).isEqualTo(99L);
        assertThat(reseeded.failureRate()).isEqualTo(base.failureRate());
    }
}
