package com.lms.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.events.EventMetadata;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventMetadataMapperTest {

    @Test
    void roundTripsWithAllFields() {
        EventMeta meta =
                new EventMeta(
                        UUID.fromString("018f9a00-0000-7000-8000-000000000001"),
                        "customer.registered",
                        1,
                        Instant.parse("2026-10-11T10:15:30.123Z"),
                        "customer",
                        "cust-1",
                        "corr-1",
                        UUID.fromString("018f9a00-0000-7000-8000-000000000002"),
                        "trace-abc",
                        "customer-service");

        EventMetadata avro = EventMetadataMapper.toAvro(meta);
        assertThat(EventMetadataMapper.fromAvro(avro)).isEqualTo(meta);
    }

    @Test
    void roundTripsWithNullableFieldsAbsent() {
        EventMeta meta =
                new EventMeta(
                        Uuid7.generate(Instant.now()),
                        "customer.registered",
                        2,
                        Instant.parse("2026-10-11T10:15:30Z"),
                        "customer",
                        "cust-9",
                        "corr-9",
                        null,
                        null,
                        "customer-service");

        EventMetadata avro = EventMetadataMapper.toAvro(meta);
        assertThat(avro.getCausationId()).isNull();
        assertThat(avro.getTraceId()).isNull();
        assertThat(EventMetadataMapper.fromAvro(avro)).isEqualTo(meta);
    }
}
