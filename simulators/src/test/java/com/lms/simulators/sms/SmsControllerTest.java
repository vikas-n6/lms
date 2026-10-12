package com.lms.simulators.sms;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.sms.SmsController.SendRequest;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class SmsControllerTest {

    private final SmsStore store = new SmsStore(Clock.systemUTC());
    private final SmsController controller =
            new SmsController(store, new SimBehavior(new SimConfigRegistry()));

    @Test
    void sendThenReadOutbox() {
        controller.send(new SendRequest("9000000001", "OTP 123456"));
        assertThat(controller.outbox("9000000001"))
                .extracting(SmsMessage::body)
                .containsExactly("OTP 123456");
        assertThat(controller.outbox(null)).hasSize(1);
    }
}
