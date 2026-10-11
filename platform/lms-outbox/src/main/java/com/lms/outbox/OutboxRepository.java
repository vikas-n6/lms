package com.lms.outbox;

import java.util.List;
import java.util.UUID;

/**
 * Persistence port for the outbox. The relay uses {@link #lockUnpublishedBatch} with {@code FOR
 * UPDATE SKIP LOCKED} so multiple relay instances never publish the same row twice.
 */
public interface OutboxRepository {

    void append(OutboxRecord record);

    /** Lock and return up to {@code limit} unpublished rows, oldest first (SKIP LOCKED). */
    List<OutboxRecord> lockUnpublishedBatch(int limit);

    void markPublished(UUID id);

    void recordFailure(UUID id, String error);
}
