package com.lms.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Shared platform configuration pulled in by every service on the classpath.
 *
 * <p>Provides the single {@link Clock} that all business code injects (never {@code Instant.now()},
 * per CLAUDE.md): UTC, because instants are stored in UTC. Tests override this bean with a fixed or
 * adjustable clock. Also enables {@link CurrencyProperties}.
 */
@AutoConfiguration
@EnableConfigurationProperties(CurrencyProperties.class)
public class LmsConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * No-override source by default: a service that can read the dev business-date key (Redis)
     * supplies its own {@link BusinessDateSource} adapter, which this backs off to.
     */
    @Bean
    @ConditionalOnMissingBean
    public BusinessDateSource businessDateSource() {
        return BusinessDateSource.none();
    }

    @Bean
    @ConditionalOnMissingBean
    public BusinessCalendar businessCalendar(BusinessDateSource source, Clock clock) {
        return new ConfigurableBusinessCalendar(source, clock);
    }
}
