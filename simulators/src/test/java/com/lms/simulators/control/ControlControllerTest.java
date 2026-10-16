package com.lms.simulators.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.config.SimulatorConfig;
import com.lms.simulators.sms.SmsStore;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class ControlControllerTest {

    private final SimConfigRegistry registry = new SimConfigRegistry();
    private final SmsStore smsStore = new SmsStore(Clock.systemUTC());
    private final ControlController controller = new ControlController(registry, smsStore);

    @Test
    void getReturnsHealthyDefaults() {
        assertThat(controller.get("bureau").failureRate()).isZero();
        assertThat(controller.all()).containsKeys("bureau", "kyc", "bank", "sms");
    }

    @Test
    void putReplacesConfig() {
        SimulatorConfig cfg = new SimulatorConfig(1, 2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 5);
        assertThat(controller.put("bank", cfg)).isEqualTo(cfg);
        assertThat(controller.get("bank")).isEqualTo(cfg);
    }

    @Test
    void presetPerSimulatorAndForAll() {
        assertThat(controller.applyPreset("bureau", "down", 1L).failureRate()).isEqualTo(1.0);
        assertThat(controller.applyPresetToAll("flaky", 2L).get("kyc").failureRate())
                .isEqualTo(0.3);
    }

    @Test
    void resetRestoresHealthyAndClearsSms() {
        controller.applyPreset("bureau", "down", 1L);
        smsStore.send("9000000001", "x");
        controller.reset();
        assertThat(controller.get("bureau").failureRate()).isZero();
        assertThat(smsStore.outbox()).isEmpty();
    }

    @Test
    void unknownSimulatorOrPresetThrows() {
        assertThatThrownBy(() -> controller.get("nope"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.applyPreset("bureau", "weird", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
