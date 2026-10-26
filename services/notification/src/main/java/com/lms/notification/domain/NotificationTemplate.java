package com.lms.notification.domain;

import java.util.Objects;

/**
 * A versioned notification template (phase prompt task 5). The body carries {@code {placeholder}}
 * tokens rendered by {@link TemplateRenderer}. Texts are configuration, never hard-coded in Java.
 *
 * @param id template id
 * @param channel the delivery channel
 * @param version template version
 * @param body the body with placeholder tokens
 */
public record NotificationTemplate(String id, Channel channel, int version, String body) {

    public NotificationTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(body, "body");
        if (version < 1) {
            throw new IllegalArgumentException("template version must be >= 1");
        }
    }

    /** Render this template with the given placeholder values. */
    public String render(java.util.Map<String, String> values) {
        return TemplateRenderer.render(body, values);
    }
}
