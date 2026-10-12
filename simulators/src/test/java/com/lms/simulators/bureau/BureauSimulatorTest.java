package com.lms.simulators.bureau;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.config.SimConfigRegistry;
import org.junit.jupiter.api.Test;

class BureauSimulatorTest {

    private final BureauSimulator bureau =
            new BureauSimulator(new SimBehavior(new SimConfigRegistry()));

    @Test
    void personaPrefixesOverrideTheScore() {
        assertThat(bureau.report("x", "9000000001")).isEqualTo(new BureauReport(800, 1, false));
        assertThat(bureau.report("x", "9001000001")).isEqualTo(new BureauReport(710, 1, false));
        assertThat(bureau.report("x", "9002000001")).isEqualTo(new BureauReport(560, 0, false));
        assertThat(bureau.report("x", "9003000001")).isEqualTo(new BureauReport(640, 0, false));
        assertThat(bureau.report("x", "9004000001")).isEqualTo(new BureauReport(720, 2, true));
    }

    @Test
    void scoreIsDeterministicAndInRangeWithoutPersona() {
        BureauReport a = bureau.report("subject-123", "8888000000");
        BureauReport b = bureau.report("subject-123", "8888000000");
        assertThat(a).isEqualTo(b); // deterministic
        assertThat(a.score()).isBetween(300, 900);
        assertThat(a.activeAccounts()).isBetween(0, 3);
    }

    @Test
    void pullAppliesBehaviorAndReturnsTheReportUnderHealthyDefaults() {
        // HEALTHY default: short latency, never fails.
        assertThat(bureau.pull("s", "9000000001")).isEqualTo(new BureauReport(800, 1, false));
    }
}
