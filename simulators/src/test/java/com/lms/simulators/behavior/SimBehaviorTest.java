package com.lms.simulators.behavior;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.config.SimulatorPreset;
import org.junit.jupiter.api.Test;

class SimBehaviorTest {

    @Test
    void healthyGatePasses() {
        SimBehavior behavior = new SimBehavior(new SimConfigRegistry());
        assertThatCode(() -> behavior.gate("bureau")).doesNotThrowAnyException();
    }

    @Test
    void downGateAlwaysFails() {
        SimConfigRegistry registry = new SimConfigRegistry();
        registry.applyPreset("bank", SimulatorPreset.DOWN, 1L);
        SimBehavior behavior = new SimBehavior(registry);
        assertThatThrownBy(() -> behavior.gate("bank"))
                .isInstanceOf(SimulatorUnavailableException.class);
    }

    @Test
    void duplicateCallbackDecisionFollowsTheRate() {
        SimConfigRegistry registry = new SimConfigRegistry();
        // HEALTHY: duplicateCallbackRate 0 -> never duplicate.
        SimBehavior behavior = new SimBehavior(registry);
        org.assertj.core.api.Assertions.assertThat(behavior.shouldDuplicateCallback("kyc"))
                .isFalse();
    }
}
