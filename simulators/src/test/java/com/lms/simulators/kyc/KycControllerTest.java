package com.lms.simulators.kyc;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.kyc.KycController.VerifyRequest;
import org.junit.jupiter.api.Test;

class KycControllerTest {

    private final SimConfigRegistry registry = new SimConfigRegistry();
    private final KycController controller =
            new KycController(
                    new KycSimulator(
                            new SimBehavior(registry),
                            registry,
                            Runnable::run,
                            (url, subjectId, status) -> {}));

    @Test
    void verifyReturnsStatus() {
        assertThat(controller.verify(new VerifyRequest("cust-ok", null)).status())
                .isEqualTo(KycStatus.VERIFIED);
        assertThat(controller.verify(new VerifyRequest("cust-x-FAIL", null)).status())
                .isEqualTo(KycStatus.FAILED);
    }
}
