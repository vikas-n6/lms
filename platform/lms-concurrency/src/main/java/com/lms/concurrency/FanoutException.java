package com.lms.concurrency;

/**
 * Thrown when a task fanned out through {@link Fanout} fails. The original failure is the
 * {@linkplain #getCause() cause}. This non-preview type shields callers from the preview {@code
 * StructuredTaskScope.FailedException}.
 */
public final class FanoutException extends RuntimeException {

    public FanoutException(Throwable cause) {
        super("a fanned-out task failed: " + cause, cause);
    }
}
