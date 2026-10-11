package com.lms.security.idempotency;

import java.util.Arrays;
import java.util.Objects;

/**
 * A snapshot of the first response produced for an {@code Idempotency-Key}, replayed verbatim on
 * retries with the same key and request body (docs/domain-rules.md "Idempotency and concurrency").
 *
 * @param requestFingerprint hash of the original request body, to detect key reuse with a different
 *     body
 */
public record StoredResponse(int status, byte[] body, String requestFingerprint) {

    public StoredResponse {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof StoredResponse other
                && status == other.status
                && requestFingerprint.equals(other.requestFingerprint)
                && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, requestFingerprint, Arrays.hashCode(body));
    }
}
