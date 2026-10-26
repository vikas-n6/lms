package com.lms.notification.application.port.out;

import com.lms.notification.domain.Channel;

/**
 * Sends a rendered message on a channel (phase prompt task 5), each channel a simulator with its
 * own failure settings. Throws {@link ChannelDeliveryException} on a (retryable) delivery failure.
 */
public interface ChannelGateway {

    void send(Channel channel, String recipientRef, String body);

    /** A channel delivery failure (retried up to the configured maximum, then FAILED). */
    class ChannelDeliveryException extends RuntimeException {
        private final String failureCode;

        public ChannelDeliveryException(String failureCode, String message) {
            super(message);
            this.failureCode = failureCode;
        }

        public String failureCode() {
            return failureCode;
        }
    }
}
