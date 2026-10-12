package com.lms.customer.adapters.out.events;

import com.lms.contracts.customer.CustomerRegistered;
import com.lms.customer.application.port.out.CustomerEventPublisher;
import com.lms.customer.domain.Customer;
import com.lms.events.EventHeaders;
import com.lms.events.EventMeta;
import com.lms.events.EventMetadataMapper;
import com.lms.events.Uuid7;
import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import com.lms.security.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.stereotype.Component;

/**
 * Writes {@code customer.registered} to the outbox (not straight to Kafka) in the same transaction
 * as the save (docs/architecture.md). The payload is the Avro {@link CustomerRegistered} contract,
 * serialized with the Schema-Registry-aware serializer; the row is keyed by the customer id so
 * events for one customer stay ordered.
 */
@Component
public class OutboxCustomerEventPublisher implements CustomerEventPublisher {

    static final String TOPIC = "customer.events";
    static final String EVENT_TYPE = "customer.registered";
    private static final String PRODUCER = "customer";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxRepository outbox;
    private final Serializer<Object> serializer;
    private final Clock clock;

    public OutboxCustomerEventPublisher(
            OutboxRepository outbox, Serializer<Object> serializer, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void customerRegistered(Customer customer) {
        String aggregateId = customer.id().toString();
        EventMeta meta =
                EventMeta.newEvent(
                        clock,
                        EVENT_TYPE,
                        SCHEMA_VERSION,
                        "customer",
                        aggregateId,
                        aggregateId, // correlationId: the customer flow id
                        null,
                        RequestContextHolder.current().traceId(),
                        PRODUCER);

        CustomerRegistered event =
                CustomerRegistered.newBuilder()
                        .setMetadata(EventMetadataMapper.toAvro(meta))
                        .setCustomerId(aggregateId)
                        .setDisplayName(customer.displayName())
                        .setRegisteredAt(customer.createdAt())
                        .build();

        byte[] payload = serializer.serialize(TOPIC, event);
        OutboxRecord row =
                new OutboxRecord(
                        Uuid7.generate(clock),
                        "customer",
                        aggregateId,
                        EVENT_TYPE,
                        meta.eventId(),
                        TOPIC,
                        aggregateId.getBytes(StandardCharsets.UTF_8),
                        payload,
                        EventHeaders.toHeaders(meta),
                        0,
                        null,
                        clock.instant(),
                        null);
        outbox.append(row);
    }
}
