package com.ispf.server.config;

import com.ispf.server.security.UiPackScopedAuthority;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakJwtRoleConverterTest {

    @Test
    void uiPackAppIdClaimAddsPackRoleBesideAdmin() {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .subject("admin")
                .claim("realm_access", Map.of("roles", List.of("admin")))
                .claim(UiPackScopedAuthority.CLAIM, "partner-app")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Collection<GrantedAuthority> authorities = new KeycloakJwtRoleConverter().convert(jwt);
        assertTrue(authorities.stream().anyMatch(authority -> "ROLE_admin".equals(authority.getAuthority())));
        assertTrue(authorities.stream().anyMatch(authority ->
                UiPackScopedAuthority.ROLE.equals(authority.getAuthority())));
    }
}
