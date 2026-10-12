package com.lms.simulators.kyc;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.config.SimulatorConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KycSimulatorTest {

    /** Records callbacks instead of making HTTP calls. */
    private static final class RecordingClient implements KycCallbackClient {
        final List<String> delivered = new ArrayList<>();

        @Override
        public void sendCallback(String callbackUrl, String subjectId, KycStatus finalStatus) {
            delivered.add(subjectId + ":" + finalStatus);
        }
    }

    @Test
    void decideFollowsTheSubjectSuffix() {
        assertThat(KycSimulator.decide("c-1")).isEqualTo(KycStatus.VERIFIED);
        assertThat(KycSimulator.decide("c-1-FAIL")).isEqualTo(KycStatus.FAILED);
        assertThat(KycSimulator.decide("c-1-PENDING")).isEqualTo(KycStatus.PENDING);
    }

    @Test
    void pendingSchedulesAResolvingCallback() {
        SimConfigRegistry registry = new SimConfigRegistry();
        RecordingClient client = new RecordingClient();
        KycSimulator kyc =
                new KycSimulator(new SimBehavior(registry), registry, Runnable::run, client);

        KycStatus status = kyc.verify("cust-7-PENDING", "http://customer/callback");

        assertThat(status).isEqualTo(KycStatus.PENDING);
        assertThat(client.delivered).containsExactly("cust-7-PENDING:VERIFIED");
    }

    @Test
    void verifiedDoesNotCallBack() {
        SimConfigRegistry registry = new SimConfigRegistry();
        RecordingClient client = new RecordingClient();
        KycSimulator kyc =
                new KycSimulator(new SimBehavior(registry), registry, Runnable::run, client);

        assertThat(kyc.verify("cust-ok", "http://customer/callback")).isEqualTo(KycStatus.VERIFIED);
        assertThat(client.delivered).isEmpty();
    }

    @Test
    void duplicateCallbackRateDeliversTwice() {
        SimConfigRegistry registry = new SimConfigRegistry();
        registry.put("kyc", new SimulatorConfig(0, 0, 0.0, 0.0, 1.0, 0, 1L)); // always duplicate
        RecordingClient client = new RecordingClient();
        KycSimulator kyc =
                new KycSimulator(new SimBehavior(registry), registry, Runnable::run, client);

        kyc.verify("cust-9-PENDING", "http://customer/callback");

        assertThat(client.delivered).hasSize(2);
    }
}
