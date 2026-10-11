package com.lms.security.idempotency;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/**
 * Pure idempotency decision logic, independent of the servlet layer so it can be unit-tested. A
 * state-changing request must carry an {@code Idempotency-Key}; replays with the same key and body
 * return the stored response, and the same key with a different body is a conflict (422).
 */
public final class IdempotencyGuard {

    private static final Set<String> STATE_CHANGING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyStore store;

    public IdempotencyGuard(IdempotencyStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public static boolean isStateChanging(String method) {
        return method != null && STATE_CHANGING.contains(method.toUpperCase());
    }

    public static String fingerprint(byte[] requestBody) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(sha.digest(requestBody == null ? new byte[0] : requestBody));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Decide what to do with a request, given its method, key and body fingerprint. */
    public IdempotencyDecision evaluate(String method, String key, String bodyFingerprint) {
        if (!isStateChanging(method)) {
            return new IdempotencyDecision.Proceed();
        }
        if (key == null || key.isBlank()) {
            return new IdempotencyDecision.MissingKey();
        }
        return store.find(key)
                .<IdempotencyDecision>map(
                        stored ->
                                stored.requestFingerprint().equals(bodyFingerprint)
                                        ? new IdempotencyDecision.Replay(stored)
                                        : new IdempotencyDecision.Conflict())
                .orElseGet(IdempotencyDecision.Proceed::new);
    }
}
