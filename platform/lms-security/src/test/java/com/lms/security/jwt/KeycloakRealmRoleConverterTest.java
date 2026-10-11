package com.lms.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private static Jwt jwtWith(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                claims.isEmpty() ? Map.of("sub", "user") : claims);
    }

    @Test
    void mapsRealmRolesToPrefixedUppercaseAuthorities() {
        Jwt jwt = jwtWith(Map.of("realm_access", Map.of("roles", List.of("analyst", "admin"))));
        assertThat(converter.convert(jwt))
                .containsExactlyInAnyOrder(
                        new SimpleGrantedAuthority("ROLE_ANALYST"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void returnsEmptyWhenNoRealmAccess() {
        assertThat(converter.convert(jwtWith(Map.of("sub", "user")))).isEmpty();
    }

    @Test
    void returnsEmptyWhenRealmAccessHasNoRoles() {
        Jwt jwt = jwtWith(Map.of("realm_access", Map.of("other", "value")));
        assertThat((List<GrantedAuthority>) converter.convert(jwt)).isEmpty();
    }
}
