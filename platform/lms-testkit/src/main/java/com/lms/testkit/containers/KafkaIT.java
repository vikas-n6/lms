package com.lms.testkit.containers;

import org.testcontainers.kafka.KafkaContainer;

/**
 * Base for integration tests needing Kafka (KRaft, no ZooKeeper). Uses the Apache Kafka native
 * image. Requires Docker; executed under Failsafe (deferred where Docker is absent).
 */
public abstract class KafkaIT {

    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.0.0");

    static {
        KAFKA.start();
    }

    protected static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}
