package com.lms.simulators.bureau;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.bureau.BureauController.PullRequest;
import com.lms.simulators.config.SimConfigRegistry;
import org.junit.jupiter.api.Test;

class BureauControllerTest {

    private final BureauController controller =
            new BureauController(new BureauSimulator(new SimBehavior(new SimConfigRegistry())));

    @Test
    void pullReturnsThePersonaReport() {
        assertThat(controller.pull(new PullRequest("s1", "9004000001")))
                .isEqualTo(new BureauReport(720, 2, true));
    }
}
