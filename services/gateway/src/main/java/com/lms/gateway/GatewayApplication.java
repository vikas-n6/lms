package com.lms.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Edge gateway: routes {@code /api/**} to services, validates JWTs, rate-limits per client, and
 * aggregates per-service health at {@code /api/status}. Runs on virtual threads ({@code
 * spring.threads.virtual.enabled=true}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
