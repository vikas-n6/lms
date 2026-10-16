package com.lms.simulators.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.payments.PaymentsSimulator.CreateLinkRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentsSimulatorTest {

    private final SimConfigRegistry registry = new SimConfigRegistry();
    private final List<Object> delivered = new ArrayList<>();
    private final PaymentsSimulator sim =
            new PaymentsSimulator(
                    new SimBehavior(registry),
                    registry,
                    Runnable::run,
                    (url, body) -> delivered.add(body));

    @Test
    void createLinkStoresTheLinkWithoutAutoComplete() {
        PaymentLink link =
                sim.createLink(new CreateLinkRequest("loan-1", 474_000L, "INR", false, null));

        assertThat(link.status()).isEqualTo(PaymentLink.CREATED);
        assertThat(link.url()).contains(link.linkId());
        assertThat(sim.byId(link.linkId())).contains(link);
        assertThat(delivered).isEmpty();
    }

    @Test
    void autoCompleteDeliversAConfirmationAndMarksCompleted() {
        PaymentLink link =
                sim.createLink(
                        new CreateLinkRequest(
                                "loan-2", 474_000L, "INR", true, "http://peer/callbacks/payments"));

        assertThat(delivered).hasSize(1);
        assertThat(delivered.get(0)).isInstanceOf(PaymentsSimulator.PaymentCallback.class);
        assertThat(sim.byId(link.linkId()).orElseThrow().status()).isEqualTo(PaymentLink.COMPLETED);
    }
}
