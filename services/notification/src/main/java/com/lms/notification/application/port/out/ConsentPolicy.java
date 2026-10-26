package com.lms.notification.application.port.out;

import com.lms.notification.domain.Channel;

/**
 * Whether the recipient has consented to messages on a channel (phase prompt task 5: "respect
 * consent"). Customer owns consent; notification reads it through this port (a permissive default
 * here, refined with a consent cache/gateway call in a later iteration). A denied channel yields a
 * {@code SUPPRESSED} notification, never a send.
 */
public interface ConsentPolicy {

    boolean allowed(String recipientRef, Channel channel);
}
