package com.lms.outbox.relay;

import com.lms.outbox.OutboxRecord;
import com.lms.outbox.OutboxRepository;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drains the outbox and publishes to Kafka. Rows are locked with {@code FOR UPDATE SKIP LOCKED} and
 * published in order; a send failure is recorded and retried on the next tick (at-least-once, with
 * consumer-side inbox dedup giving effective exactly-once). Runs on a virtual thread (CLAUDE.md:
 * never pool virtual threads — this is a single scheduled poller).
 */
public final class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final RecordSender sender;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public OutboxRelay(OutboxRepository repository, RecordSender sender, int batchSize) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sender = Objects.requireNonNull(sender, "sender");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        this.batchSize = batchSize;
    }

    /** Publish one batch; returns how many rows were published. Safe to call directly in tests. */
    public int drainOnce() {
        List<OutboxRecord> batch = repository.lockUnpublishedBatch(batchSize);
        int published = 0;
        for (OutboxRecord record : batch) {
            try {
                sender.send(record);
                repository.markPublished(record.id());
                published++;
            } catch (RuntimeException e) {
                log.warn(
                        "Outbox publish failed for event {} on {}",
                        record.eventId(),
                        record.topic(),
                        e);
                repository.recordFailure(record.id(), e.toString());
            }
        }
        return published;
    }

    /** Start polling every {@code intervalMillis} on a single virtual thread. */
    public void start(long intervalMillis) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        ThreadFactory factory = Thread.ofVirtual().name("outbox-relay-", 0).factory();
        scheduler = Executors.newSingleThreadScheduledExecutor(factory);
        scheduler.scheduleWithFixedDelay(
                this::drainQuietly, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (running.compareAndSet(true, false) && scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void drainQuietly() {
        try {
            drainOnce();
        } catch (RuntimeException e) {
            log.error("Outbox relay tick failed", e);
        }
    }
}
