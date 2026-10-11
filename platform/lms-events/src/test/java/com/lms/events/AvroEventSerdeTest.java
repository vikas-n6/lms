package com.lms.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.customer.CustomerRegistered;
import com.lms.contracts.events.EventMetadata;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.time.Instant;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;

/**
 * Proves the Avro/Schema-Registry serde round-trips the reference event without a running registry
 * (MockSchemaRegistryClient), so it runs on JDK 25 with no Docker.
 */
class AvroEventSerdeTest {

    private static final String TOPIC = "customer.events";

    @Test
    void serializesAndDeserializesThroughSchemaRegistry() {
        SchemaRegistryClient registry = new MockSchemaRegistryClient();
        Serializer<Object> serializer = AvroEventSerde.serializer(registry, true);
        Deserializer<Object> deserializer = AvroEventSerde.deserializer(registry);

        CustomerRegistered event =
                CustomerRegistered.newBuilder()
                        .setMetadata(
                                new EventMetadata(
                                        "018f9a00-0000-7000-8000-000000000001",
                                        "customer.registered",
                                        1,
                                        Instant.parse("2026-10-11T10:15:30.123Z"),
                                        "customer",
                                        "cust-1",
                                        "corr-1",
                                        null,
                                        "trace-abc",
                                        "customer-service"))
                        .setCustomerId("cust-1")
                        .setDisplayName("Asha")
                        .setRegisteredAt(Instant.parse("2026-10-11T10:15:30.123Z"))
                        .build();

        byte[] bytes = serializer.serialize(TOPIC, event);
        Object decoded = deserializer.deserialize(TOPIC, bytes);

        assertThat(decoded).isInstanceOf(CustomerRegistered.class).isEqualTo(event);
    }
}
