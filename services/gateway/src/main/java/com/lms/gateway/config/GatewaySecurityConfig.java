package com.lms.gateway.config;

import com.lms.gateway.ratelimit.InMemoryRateLimiter;
import com.lms.gateway.ratelimit.RateLimitFilter;
import com.lms.gateway.ratelimit.RateLimiter;
import com.lms.gateway.ratelimit.RedisRateLimiter;
import com.lms.security.jwt.KeycloakRealmRoleConverter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Gateway edge security: stateless JWT validation, Keycloak realm roles mapped to authorities,
 * {@code /api/**} restricted to ADMIN/ANALYST, and a rate-limit filter after authorization. {@code
 * /actuator/health/**} and {@code /api/status} are public.
 */
@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    @Bean
    @ConditionalOnProperty(name = "lms.gateway.ratelimit.redis", havingValue = "true")
    public RateLimiter redisRateLimiter(StringRedisTemplate redis) {
        return new RedisRateLimiter(redis, 100, Duration.ofMinutes(1));
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter inMemoryRateLimiter(Clock clock) {
        return new InMemoryRateLimiter(100, Duration.ofMinutes(1), clock);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            RateLimiter rateLimiter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/api/status")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .hasAnyRole("ADMIN", "ANALYST")
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter)))
                .addFilterAfter(new RateLimitFilter(rateLimiter), AuthorizationFilter.class);
        return http.build();
    }
}
