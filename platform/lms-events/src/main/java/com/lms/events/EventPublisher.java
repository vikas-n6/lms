package com.lms.events;

/**
 * Port for publishing events. Per docs/architecture.md, the only production implementation writes
 * to the transactional outbox (never straight to Kafka from a request handler); the relay drains
 * it.
 */
public interface EventPublisher {

    /** Publish an event to the given topic, keyed by its aggregate id. */
    void publish(String topic, EventEnvelope<?> event);
}
