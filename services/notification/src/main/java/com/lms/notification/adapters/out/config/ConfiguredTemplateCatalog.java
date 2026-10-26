package com.lms.notification.adapters.out.config;

import com.lms.notification.application.port.out.TemplateCatalog;
import com.lms.notification.config.NotificationProperties;
import com.lms.notification.domain.Channel;
import com.lms.notification.domain.NotificationTemplate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the domain templates and the event-type → templates routing from {@link
 * NotificationProperties} (phase prompt task 5). The only place the config shape meets the domain.
 */
@Component
public class ConfiguredTemplateCatalog implements TemplateCatalog {

    private final Map<String, NotificationTemplate> templatesById;
    private final Map<String, List<String>> routes;

    public ConfiguredTemplateCatalog(NotificationProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.templatesById =
                properties.templates().stream()
                        .collect(
                                Collectors.toMap(
                                        NotificationProperties.Template::id,
                                        t ->
                                                new NotificationTemplate(
                                                        t.id(),
                                                        Channel.valueOf(t.channel()),
                                                        t.version(),
                                                        t.body())));
        this.routes = properties.routes() == null ? Map.of() : Map.copyOf(properties.routes());
    }

    @Override
    public List<NotificationTemplate> templatesFor(String eventType) {
        return routes.getOrDefault(eventType, List.of()).stream()
                .map(templatesById::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
