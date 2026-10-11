package com.lms.outbox.relay;

import com.lms.outbox.OutboxRecord;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

/**
 * Publishes outbox rows via a Kafka producer. The producer must be configured idempotent with
 * {@code acks=all} (docs/architecture.md "Kafka rules"); this blocks for the broker acknowledgement
 * so the relay only marks a row published once the send is durable.
 */
public final class KafkaRecordSender implements RecordSender {

    private final Producer<byte[], byte[]> producer;

    public KafkaRecordSender(Producer<byte[], byte[]> producer) {
        this.producer = Objects.requireNonNull(producer, "producer");
    }

    @Override
    public void send(OutboxRecord record) {
        ProducerRecord<byte[], byte[]> producerRecord =
                new ProducerRecord<>(record.topic(), record.key(), record.payload());
        if (record.headers() != null) {
            for (Map.Entry<String, byte[]> header : record.headers().entrySet()) {
                producerRecord.headers().add(new RecordHeader(header.getKey(), header.getValue()));
            }
        }
        try {
            producer.send(producerRecord).get();
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Kafka send failed for " + record.eventId(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted sending " + record.eventId(), e);
        }
    }
}
