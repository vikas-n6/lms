package com.lms.gateway.status;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Downstream services the gateway knows about, as {@code lms.gateway.services.<name>=<base-url>}.
 * Used by {@code /api/status} to aggregate health.
 */
@ConfigurationProperties(prefix = "lms.gateway")
public record GatewayServicesProperties(Map<String, String> services) {

    public GatewayServicesProperties {
        services = services == null ? Map.of() : Map.copyOf(services);
    }
}
