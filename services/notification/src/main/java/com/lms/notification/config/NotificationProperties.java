package com.lms.notification.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Notification configuration (phase prompt task 5 / CLAUDE.md: templates and texts are never
 * hard-coded in Java). Bound from {@code lms.notification.*}; defaults live in {@code
 * application.yml}.
 *
 * @param maxAttempts channel send attempts before FAILED
 * @param quietStartHour quiet-hours window start (inclusive)
 * @param quietEndHour quiet-hours window end (exclusive)
 * @param zone business time zone for quiet hours
 * @param templates the versioned templates
 * @param routes trigger event type → the template ids to send
 */
@ConfigurationProperties(prefix = "lms.notification")
public record NotificationProperties(
        int maxAttempts,
        int quietStartHour,
        int quietEndHour,
        String zone,
        List<Template> templates,
        Map<String, List<String>> routes) {

    /**
     * @param id template id, referenced by routes
     * @param channel {@link com.lms.notification.domain.Channel} name
     * @param version template version
     * @param body body with {@code {placeholder}} tokens
     */
    public record Template(String id, String channel, int version, String body) {}
}
