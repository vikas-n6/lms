package com.lms.disbursement.application.port.out;

/**
 * De-duplicates rail callbacks on {@code (railRef, status)} (phase prompt: dedup with Redis plus a
 * unique constraint; the DB unique key is the final guard). Implemented by a Redis+JDBC adapter.
 */
public interface RailCallbackDedupStore {

    /**
     * Record the (railRef, status) pair; returns true if it was newly seen, false if a duplicate.
     */
    boolean firstSeen(String railRef, String status);
}
