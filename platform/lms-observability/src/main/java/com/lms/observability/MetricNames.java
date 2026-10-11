package com.lms.observability;

/**
 * Standard metric names used across services, so dashboards and alerts are consistent. Keep names
 * dot-delimited and tag-friendly (Micrometer converts per registry). Business metrics are added by
 * later phases; these are the platform-wide ones.
 */
public final class MetricNames {

    /** Count of events published by the outbox relay. Tag: {@code topic}. */
    public static final String OUTBOX_PUBLISHED = "lms.outbox.published";

    /** Count of events that reached a dead-letter topic. Tags: {@code topic}, {@code reason}. */
    public static final String OUTBOX_DEAD_LETTERED = "lms.outbox.dead_lettered";

    /** Count of consumer messages skipped as duplicates by the inbox. Tag: {@code topic}. */
    public static final String INBOX_DUPLICATE_SKIPPED = "lms.inbox.duplicate_skipped";

    /** Count of requests rejected or replayed by the idempotency filter. Tag: {@code outcome}. */
    public static final String IDEMPOTENCY_OUTCOME = "lms.idempotency.outcome";

    private MetricNames() {}
}
