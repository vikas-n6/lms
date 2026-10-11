package com.lms.security.jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps Keycloak {@code realm_access.roles} onto Spring Security {@code ROLE_*} authorities, so
 * {@code @PreAuthorize("hasRole('ANALYST')")} and request-matcher role checks work against Keycloak
 * tokens.
 */
public final class KeycloakRealmRoleConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object roles = map.get("roles");
        if (!(roles instanceof Collection<?> roleList)) {
            return List.of();
        }
        return roleList.stream()
                .map(Object::toString)
                .map(
                        role ->
                                (GrantedAuthority)
                                        new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .toList();
    }
}
