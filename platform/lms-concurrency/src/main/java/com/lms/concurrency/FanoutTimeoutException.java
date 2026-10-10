package com.lms.concurrency;

import java.time.Duration;

/**
 * Thrown when a {@link Fanout} call does not finish within its deadline. This non-preview type
 * shields callers from the preview {@code StructuredTaskScope.TimeoutException}.
 */
public final class FanoutTimeoutException extends RuntimeException {

    public FanoutTimeoutException(Duration timeout) {
        super("fanned-out tasks did not complete within " + timeout);
    }
}
