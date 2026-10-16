package com.lms.security.callback;

/**
 * Raised when an inbound callback's signature header is missing, malformed, stale or does not match
 * (phase prompt: no callback is accepted without a valid signature). Controllers map it to a 401.
 */
public class InvalidCallbackSignatureException extends RuntimeException {
    public InvalidCallbackSignatureException() {
        super("invalid or missing callback signature");
    }
}
