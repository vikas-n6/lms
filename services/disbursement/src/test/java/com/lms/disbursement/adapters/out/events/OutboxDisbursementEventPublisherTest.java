package com.lms.disbursement.adapters.out.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.disbursement.DisbursementSucceeded;
import com.lms.disbursement.domain.Disbursement;
import com.lms.events.AvroEventSerde;
import com.lms.money.Money;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import com.lms.testkit.time.MutableClock;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;

class OutboxDisbursementEventPublisherTest {

    private static final Currency INR = Currency.getInstance("INR");

    private static Money inr(long minor) {
        return Money.of(minor, INR);
    }

    @Test
    void appendsSucceededAsAnAvroOutboxRowKeyedByLoan() {
        SchemaRegistryClient registry = new MockSchemaRegistryClient();
        Serializer<Object> serializer = AvroEventSerde.serializer(registry, true);
        Deserializer<Object> deserializer = AvroEventSerde.deserializer(registry);
        RecordingOutbox outbox = new RecordingOutbox();
        MutableClock clock = MutableClock.utc(Instant.parse("2026-10-20T05:00:00Z"));

        OutboxDisbursementEventPublisher publisher =
                new OutboxDisbursementEventPublisher(outbox, serializer, clock);

        UUID loanId = UUID.randomUUID();
        Disbursement d =
                Disbursement.initiate(
                                UUID.randomUUID(),
                                loanId,
                                "LN-2026-000001",
                                inr(5_000_000L),
                                inr(100_000L),
                                inr(18_000L),
                                inr(4_882_000L),
                                clock.instant())
                        .withAttemptStarted(clock.instant())
                        .succeeded("RAIL-9", "UTR-9", LocalDate.of(2026, 10, 20), clock.instant());

        publisher.succeeded(d);

        assertThat(outbox.appended).hasSize(1);
        OutboxRecord row = outbox.appended.get(0);
        assertThat(row.topic()).isEqualTo("disbursement.events");
        assertThat(row.eventType()).isEqualTo("disbursement.succeeded");
        assertThat(row.aggregateId()).isEqualTo(loanId.toString());
        assertThat(row.publishedAt()).isNull();

        DisbursementSucceeded decoded =
                (DisbursementSucceeded) deserializer.deserialize(row.topic(), row.payload());
        assertThat(decoded.getLoanId()).isEqualTo(loanId.toString());
        assertThat(decoded.getNetAmount().getMinorUnits()).isEqualTo(4_882_000L);
        assertThat(decoded.getRailReference()).isEqualTo("RAIL-9");
        assertThat(decoded.getMetadata().getProducer()).isEqualTo("disbursement");
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
