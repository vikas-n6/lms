package com.lms.security.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Store for idempotent responses. Production uses Redis ({@code SET key NX EX 24h}) backed by a
 * Postgres unique constraint as the final guard (docs/architecture.md "Redis rules"). Tests use the
 * in-memory implementation.
 */
public interface IdempotencyStore {

    Optional<StoredResponse> find(String key);

    /** Store the response for a key if absent; returns false if a value already existed. */
    boolean putIfAbsent(String key, StoredResponse response, Duration ttl);
}
