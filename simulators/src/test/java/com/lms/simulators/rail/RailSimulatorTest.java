package com.lms.simulators.rail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.config.SimulatorConfig;
import com.lms.simulators.rail.RailSimulator.PayoutRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RailSimulatorTest {

    private final SimConfigRegistry registry = new SimConfigRegistry();
    private final List<Object> delivered = new ArrayList<>();
    private final RailSimulator sim =
            new RailSimulator(
                    new SimBehavior(registry),
                    registry,
                    Runnable::run,
                    (url, body) -> delivered.add(body));

    private static PayoutRequest req(String key) {
        return new PayoutRequest(key, "loan-1", 4_882_000L, "INR", "http://peer/callbacks/rail");
    }

    @Test
    void outcomeIsDeterministicInTheKey() {
        assertThat(RailSimulator.decide(req("disb-1")).outcome()).isEqualTo(Payout.SUCCESS);
        Payout failed = RailSimulator.decide(req("disb-1-FAIL"));
        assertThat(failed.outcome()).isEqualTo(Payout.FAILED);
        assertThat(failed.failureCode()).isEqualTo("RAIL_DECLINED");
    }

    @Test
    void payoutIsIdempotentOnTheKeyAndSchedulesOneCallback() {
        Payout first = sim.payout(req("disb-7"));
        Payout second = sim.payout(req("disb-7"));

        assertThat(second).isEqualTo(first);
        assertThat(sim.byKey("disb-7")).contains(first);
        // The repeat POST records nothing new and schedules no second callback.
        assertThat(delivered).hasSize(1);
    }

    @Test
    void lostResponseStillRecordsThePayoutQueryableByKey() {
        // unknownOutcomeRate = 1: the payout is recorded and callback scheduled, but the POST
        // response is lost, so the caller must query status by key rather than re-pay.
        registry.put("rail", new SimulatorConfig(0, 0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0, 1L));

        assertThatThrownBy(() -> sim.payout(req("disb-9")))
                .isInstanceOf(RailSimulator.ResponseLostException.class);
        assertThat(sim.byKey("disb-9")).isPresent();
        assertThat(sim.byKey("disb-9").orElseThrow().succeeded()).isTrue();
    }

    @Test
    void unknownKeyHasNoStatus() {
        assertThat(sim.byKey("never")).isEmpty();
    }
}
