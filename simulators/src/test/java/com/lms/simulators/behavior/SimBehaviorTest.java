package com.lms.simulators.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.config.SimulatorConfig;
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
        assertThat(behavior.shouldDuplicateCallback("kyc")).isFalse();
    }

    @Test
    void phase3KnobsAtZeroNeverTrigger() {
        SimConfigRegistry registry = new SimConfigRegistry(); // all HEALTHY (rates 0)
        SimBehavior behavior = new SimBehavior(registry);
        assertThat(behavior.shouldLoseResponse("rail")).isFalse();
        assertThat(behavior.shouldDeliverOutOfOrder("rail")).isFalse();
        assertThat(behavior.shouldReturn("mandate")).isFalse();
    }

    @Test
    void phase3KnobsAtOneAlwaysTrigger() {
        SimConfigRegistry registry = new SimConfigRegistry();
        registry.put("rail", new SimulatorConfig(0, 0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0, 1L));
        SimBehavior behavior = new SimBehavior(registry);
        assertThat(behavior.shouldLoseResponse("rail")).isTrue();
        assertThat(behavior.shouldDeliverOutOfOrder("rail")).isTrue();
        assertThat(behavior.shouldReturn("rail")).isTrue();
    }
}
