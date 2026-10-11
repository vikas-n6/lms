package com.lms.security.idempotency;

/**
 * The outcome of checking an incoming request against the {@link IdempotencyStore}. A sealed type
 * so callers switch over it exhaustively.
 */
public sealed interface IdempotencyDecision {

    /** No stored result: let the request run and capture its response. */
    record Proceed() implements IdempotencyDecision {}

    /** Same key and body seen before: replay the stored response. */
    record Replay(StoredResponse response) implements IdempotencyDecision {}

    /** Same key but a different body: reject with 422 (docs/domain-rules.md). */
    record Conflict() implements IdempotencyDecision {}

    /** A state-changing request arrived without the required Idempotency-Key header. */
    record MissingKey() implements IdempotencyDecision {}
}
