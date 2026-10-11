package com.lms.outbox.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdempotentConsumerTest {

    private final InboxStore inbox = new InMemoryInboxStore();
    private final IdempotentConsumer consumer = new IdempotentConsumer(inbox);

    @Test
    void runsHandlerOnceAndSkipsDuplicates() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger();

        assertThat(consumer.process(eventId, "loan.events", runs::incrementAndGet)).isTrue();
        assertThat(consumer.process(eventId, "loan.events", runs::incrementAndGet)).isFalse();
        assertThat(consumer.process(eventId, "loan.events", runs::incrementAndGet)).isFalse();

        assertThat(runs).hasValue(1);
    }

    @Test
    void differentEventIdsEachRun() {
        AtomicInteger runs = new AtomicInteger();
        assertThat(consumer.process(UUID.randomUUID(), "t", runs::incrementAndGet)).isTrue();
        assertThat(consumer.process(UUID.randomUUID(), "t", runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(2);
    }
}
