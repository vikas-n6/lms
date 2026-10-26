package com.lms.notification.adapters.out.consent;

import com.lms.notification.application.port.out.ConsentPolicy;
import com.lms.notification.domain.Channel;
import org.springframework.stereotype.Component;

/**
 * Permissive consent for the learning project (CLAUDE.md: compliance is mocked behind a permissive
 * policy). Consent is granted on every channel; a real deployment would read the customer's
 * consents through a cache fed by {@code customer.events} or a gateway call, and this is where a
 * withheld channel would return false (yielding a SUPPRESSED notification).
 */
@Component
public class PermissiveConsentPolicy implements ConsentPolicy {

    @Override
    public boolean allowed(String recipientRef, Channel channel) {
        return true;
    }
}
