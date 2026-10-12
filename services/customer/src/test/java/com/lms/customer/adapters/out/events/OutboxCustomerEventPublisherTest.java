package com.lms.customer.adapters.out.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.customer.CustomerRegistered;
import com.lms.customer.domain.Customer;
import com.lms.events.AvroEventSerde;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import com.lms.testkit.time.MutableClock;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;

class OutboxCustomerEventPublisherTest {

    @Test
    void appendsCustomerRegisteredAsAnAvroOutboxRow() {
        SchemaRegistryClient registry = new MockSchemaRegistryClient();
        Serializer<Object> serializer = AvroEventSerde.serializer(registry, true);
        Deserializer<Object> deserializer = AvroEventSerde.deserializer(registry);
        RecordingOutbox outbox = new RecordingOutbox();
        MutableClock clock = MutableClock.utc(Instant.parse("2026-10-11T10:00:00Z"));

        OutboxCustomerEventPublisher publisher =
                new OutboxCustomerEventPublisher(outbox, serializer, clock);
        Customer customer =
                Customer.register(clock, "Asha Rao", "9876543210", LocalDate.of(1990, 1, 1));

        publisher.customerRegistered(customer);

        assertThat(outbox.appended).hasSize(1);
        OutboxRecord row = outbox.appended.get(0);
        assertThat(row.topic()).isEqualTo("customer.events");
        assertThat(row.eventType()).isEqualTo("customer.registered");
        assertThat(row.aggregateId()).isEqualTo(customer.id().toString());
        assertThat(row.publishedAt()).isNull();

        CustomerRegistered decoded =
                (CustomerRegistered) deserializer.deserialize(row.topic(), row.payload());
        assertThat(decoded.getCustomerId()).isEqualTo(customer.id().toString());
        assertThat(decoded.getDisplayName()).isEqualTo("Asha");
        assertThat(decoded.getMetadata().getEventType()).isEqualTo("customer.registered");
        assertThat(decoded.getMetadata().getProducer()).isEqualTo("customer");
    }

    private static final class RecordingOutbox implements OutboxRepository {
        final List<OutboxRecord> appended = new ArrayList<>();

        @Override
        public void append(OutboxRecord record) {
            appended.add(record);
        }

        @Override
        public List<OutboxRecord> lockUnpublishedBatch(int limit) {
            return List.of();
        }

        @Override
        public void markPublished(UUID id) {}

        @Override
        public void recordFailure(UUID id, String error) {}
    }
}
