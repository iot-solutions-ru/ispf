package com.ispf.server.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps Keycloak realm roles (admin, operator) to Spring Security authorities.
 */
public class KeycloakJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            Object roles = realmMap.get("roles");
            if (roles instanceof Collection<?> roleList) {
                for (Object role : roleList) {
                    if (role != null) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString()));
                    }
                }
            }
        }
        return authorities;
    }
}
