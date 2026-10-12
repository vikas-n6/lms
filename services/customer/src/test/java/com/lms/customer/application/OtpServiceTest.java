package com.lms.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.customer.application.port.out.OtpStore;
import com.lms.customer.application.port.out.SmsSender;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OtpServiceTest {

    /** A single-use in-memory OTP store (no TTL expiry simulated). */
    private static final class FakeStore implements OtpStore {
        final Map<String, String> codes = new HashMap<>();

        @Override
        public void store(String key, String code, Duration ttl) {
            codes.put(key, code);
        }

        @Override
        public boolean consume(String key, String code) {
            return code.equals(codes.get(key)) && codes.remove(key) != null;
        }
    }

    private static final class FakeSms implements SmsSender {
        String lastPhone;
        String lastMessage;

        @Override
        public void send(String phone, String message) {
            this.lastPhone = phone;
            this.lastMessage = message;
        }
    }

    private final FakeStore store = new FakeStore();
    private final FakeSms sms = new FakeSms();
    private final OtpService service =
            new OtpService(store, sms, () -> "123456", Duration.ofMinutes(5));

    @Test
    void issueStoresTheCodeAndTextsIt() {
        service.issue("lms:customer:otp:c1", "9000000001");
        assertThat(store.codes).containsEntry("lms:customer:otp:c1", "123456");
        assertThat(sms.lastPhone).isEqualTo("9000000001");
        assertThat(sms.lastMessage).contains("123456");
    }

    @Test
    void verifySucceedsOnceThenFails() {
        service.issue("k", "9000000001");
        assertThat(service.verify("k", "123456")).isTrue(); // consumed
        assertThat(service.verify("k", "123456")).isFalse(); // single-use
    }

    @Test
    void verifyRejectsWrongOrNullCode() {
        service.issue("k", "9000000001");
        assertThat(service.verify("k", "000000")).isFalse();
        assertThat(service.verify("k", null)).isFalse();
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new OtpService(store, sms, () -> "1", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
