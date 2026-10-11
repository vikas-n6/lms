package com.lms.outbox.relay;

import com.lms.outbox.OutboxRecord;

/** Sends an outbox row to the broker. The relay never talks to Kafka directly. */
public interface RecordSender {

    /**
     * Publish the record synchronously, throwing if the broker does not acknowledge.
     * Implementations must be idempotent producers ({@code acks=all}) so a retried send does not
     * duplicate.
     */
    void send(OutboxRecord record);
}
