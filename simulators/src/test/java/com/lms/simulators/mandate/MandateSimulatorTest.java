package com.lms.simulators.mandate;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.mandate.MandateSimulator.DebitRequest;
import com.lms.simulators.mandate.MandateSimulator.DebitResult;
import com.lms.simulators.mandate.MandateSimulator.RegisterRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MandateSimulatorTest {

    private final SimConfigRegistry registry = new SimConfigRegistry();
    private final List<Object> delivered = new ArrayList<>();
    private final MandateSimulator sim =
            new MandateSimulator(
                    new SimBehavior(registry),
                    registry,
                    Runnable::run,
                    (url, body) -> delivered.add(body));

    @Test
    void registerIsActiveByDefaultAndFailsForMarkedCustomer() {
        Mandate ok = sim.register(new RegisterRequest("loan-1", "cust-1", null));
        assertThat(ok.isActive()).isTrue();

        Mandate no = sim.register(new RegisterRequest("loan-2", "cust-NOMANDATE", null));
        assertThat(no.status()).isEqualTo(Mandate.FAILED);
    }

    @Test
    void debitSucceedsForActiveMandateAndIsIdempotent() {
        Mandate mandate = sim.register(new RegisterRequest("loan-1", "cust-1", null));
        DebitRequest request =
                new DebitRequest(
                        "debit-1",
                        mandate.mandateId(),
                        "loan-1",
                        474_000L,
                        "INR",
                        "http://peer/cb");

        DebitResult first = sim.debit(request);
        DebitResult second = sim.debit(request);

        assertThat(first.succeeded()).isTrue();
        assertThat(second).isEqualTo(first);
        assertThat(delivered).hasSize(1); // one callback, not two
    }

    @Test
    void debitFailsWhenMandateMissingOrKeyMarked() {
        DebitResult noMandate =
                sim.debit(new DebitRequest("d-x", "MND-missing", "loan-1", 1L, "INR", null));
        assertThat(noMandate.outcome()).isEqualTo(DebitResult.FAILED);
        assertThat(noMandate.failureCode()).isEqualTo("NO_MANDATE");

        Mandate mandate = sim.register(new RegisterRequest("loan-9", "cust-9", null));
        DebitResult marked =
                sim.debit(
                        new DebitRequest("d-FAIL", mandate.mandateId(), "loan-9", 1L, "INR", null));
        assertThat(marked.outcome()).isEqualTo(DebitResult.FAILED);
    }

    @Test
    void cancelMarksMandateCancelled() {
        Mandate mandate = sim.register(new RegisterRequest("loan-1", "cust-1", null));
        assertThat(sim.cancel(mandate.mandateId())).isTrue();
        assertThat(sim.byId(mandate.mandateId()).orElseThrow().status())
                .isEqualTo(Mandate.CANCELLED);
        assertThat(sim.cancel("MND-unknown")).isFalse();
    }

    @Test
    void returnedDebitSchedulesASecondCallback() {
        registry.put(
                "mandate",
                new com.lms.simulators.config.SimulatorConfig(
                        0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0, 1L)); // returnRate = 1
        Mandate mandate = sim.register(new RegisterRequest("loan-1", "cust-1", null));
        sim.debit(
                new DebitRequest(
                        "debit-r",
                        mandate.mandateId(),
                        "loan-1",
                        474_000L,
                        "INR",
                        "http://peer/cb"));
        // success callback, then a RETURNED callback.
        assertThat(delivered).hasSize(2);
        assertThat(delivered.get(1)).isInstanceOf(MandateSimulator.DebitCallback.class);
        assertThat(((MandateSimulator.DebitCallback) delivered.get(1)).status())
                .isEqualTo(DebitResult.RETURNED);
    }
}
