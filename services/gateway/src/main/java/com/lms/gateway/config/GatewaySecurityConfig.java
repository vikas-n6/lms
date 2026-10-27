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
import org.springframework.http.HttpMethod;
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
                                        // Signed provider callbacks bypass JWT; the downstream
                                        // service verifies the HMAC signature (phase prompt S6).
                                        .requestMatchers("/callbacks/**")
                                        .permitAll()
                                        // Simulator control is admin-only (phase prompt S6).
                                        .requestMatchers("/api/sim/**")
                                        .hasRole("ADMIN")
                                        // Disbursement retry/cancel are admin-only (list/detail are
                                        // GETs, covered by the read rule below).
                                        .requestMatchers(HttpMethod.POST, "/api/disbursements/**")
                                        .hasRole("ADMIN")
                                        // Admin manual receipt is admin-only.
                                        .requestMatchers(HttpMethod.POST, "/api/repayments/manual")
                                        .hasRole("ADMIN")
                                        // Write-off request + approval are admin-only; the
                                        // two-person rule (distinct admins) is enforced in the
                                        // service (phase prompt task 2).
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/write-off-requests/**",
                                                "/api/loans/*/write-off-requests")
                                        .hasRole("ADMIN")
                                        // Collection case actions (assign/promise/escalate/resolve)
                                        // are worked by admins and analyst agents.
                                        .requestMatchers(HttpMethod.POST, "/api/collections/**")
                                        .hasAnyRole("ADMIN", "ANALYST")
                                        // Reconciliation resolve/adjust are admin-only.
                                        .requestMatchers(HttpMethod.POST, "/api/recon/**")
                                        .hasRole("ADMIN")
                                        // Reporting dashboards, audit log and the live dashboard
                                        // SSE stream are back-office only (admin + analyst), never
                                        // the customer read rule below.
                                        .requestMatchers(
                                                "/api/reporting/**",
                                                "/api/audit/**",
                                                "/api/stream/**")
                                        .hasAnyRole("ADMIN", "ANALYST")
                                        // Admin ops (business date, job triggers, dev disbursement/
                                        // repayment) and ledger reversal are admin-only.
                                        .requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/journal-entries/*/reverse")
                                        .hasRole("ADMIN")
                                        // Reads: admin + analyst + the customer (own records).
                                        .requestMatchers(HttpMethod.GET, "/api/**")
                                        .hasAnyRole("ADMIN", "ANALYST", "CUSTOMER")
                                        // Writes: admin and the acting customer; analyst is
                                        // read-only.
                                        // Per-record ownership is enforced in the services
                                        // (backlog).
                                        .requestMatchers("/api/**")
                                        .hasAnyRole("ADMIN", "CUSTOMER")
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
