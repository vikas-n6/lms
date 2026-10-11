package com.lms.outbox.retry;

import java.util.List;

/**
 * Computes retry-topic escalation for a consumer that keeps failing. Each base topic has {@code
 * <topic>.retry.5s}, {@code <topic>.retry.1m}, {@code <topic>.retry.10m} and {@code <topic>.dlt}
 * (docs/architecture.md "Topic catalogue"). After the retry tiers are exhausted the record goes to
 * the dead-letter topic.
 */
public final class RetryPolicy {

    /** Retry tier suffixes in escalation order. */
    public static final List<String> RETRY_TIERS = List.of("retry.5s", "retry.1m", "retry.10m");

    private RetryPolicy() {}

    public static String retryTopic(String baseTopic, String tier) {
        return baseTopic + "." + tier;
    }

    public static String deadLetterTopic(String baseTopic) {
        return baseTopic + ".dlt";
    }

    /**
     * The topic a record should be routed to after {@code failedAttempts} failures (1-based): the
     * next retry tier, or the DLT once all tiers are exhausted.
     */
    public static String nextTopic(String baseTopic, int failedAttempts) {
        if (failedAttempts < 1) {
            throw new IllegalArgumentException("failedAttempts must be >= 1");
        }
        if (failedAttempts <= RETRY_TIERS.size()) {
            return retryTopic(baseTopic, RETRY_TIERS.get(failedAttempts - 1));
        }
        return deadLetterTopic(baseTopic);
    }
}
