package com.lms.security.idempotency;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link IdempotencyStore} for tests and local single-node runs (TTL is ignored). */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, StoredResponse> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredResponse> find(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    @Override
    public boolean putIfAbsent(String key, StoredResponse response, Duration ttl) {
        return entries.putIfAbsent(key, response) == null;
    }
}
