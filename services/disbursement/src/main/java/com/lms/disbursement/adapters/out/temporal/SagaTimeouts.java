package com.lms.disbursement.adapters.out.temporal;

/**
 * Saga timing, carried in the workflow input so it is recorded in history and the workflow stays
 * deterministic (CLAUDE.md: no hard-coded business parameters; these come from service
 * configuration, 30-minute payout timeout in production, 10 seconds in tests).
 *
 * @param payoutTimeoutSeconds how long to await the rail callback before polling status
 * @param pollRounds bounded status-poll rounds before parking in manual review
 * @param pollBackoffSeconds wait between status polls
 * @param maxAttempts maximum payout attempts
 * @param retryBackoffSeconds initial backoff before a retried attempt
 * @param retryMultiplier exponential multiplier for the retry backoff
 * @param activityTimeoutSeconds start-to-close timeout for each activity (infrastructure)
 */
public record SagaTimeouts(
        long payoutTimeoutSeconds,
        int pollRounds,
        long pollBackoffSeconds,
        int maxAttempts,
        long retryBackoffSeconds,
        double retryMultiplier,
        long activityTimeoutSeconds) {}
