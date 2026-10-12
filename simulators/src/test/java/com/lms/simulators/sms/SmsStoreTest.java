package com.lms.simulators.sms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SmsStoreTest {

    private final Instant now = Instant.parse("2026-10-12T09:00:00Z");
    private final SmsStore store = new SmsStore(Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void storesAndReadsBackByRecipient() {
        store.send("9000000001", "Your OTP is 123456");
        store.send("9000000002", "Your OTP is 654321");
        store.send("9000000001", "Welcome");

        assertThat(store.outbox()).hasSize(3);
        assertThat(store.outboxFor("9000000001"))
                .extracting(SmsMessage::body)
                .containsExactly("Your OTP is 123456", "Welcome");
        assertThat(store.outbox().get(0).sentAt()).isEqualTo(now);
    }

    @Test
    void clearEmptiesTheOutbox() {
        store.send("9000000001", "x");
        store.clear();
        assertThat(store.outbox()).isEmpty();
    }
}
