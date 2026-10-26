package com.lms.notification.application.port.out;

import com.lms.notification.domain.NotificationTemplate;
import java.util.List;

/**
 * Resolves the templates to send for a trigger event type (phase prompt task 5). Backed by the
 * versioned {@code lms.notification.*} configuration.
 */
public interface TemplateCatalog {

    /** The templates (channel + body) configured for {@code eventType}; empty if none. */
    List<NotificationTemplate> templatesFor(String eventType);
}
