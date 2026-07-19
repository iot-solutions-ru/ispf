package com.ispf.server.tenant;

import com.ispf.server.config.TenantIsolationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcTenantClaimMappingTest {

    @Test
    void resolveTenantIdFromJwtClaim() {
        TenantIsolationProperties properties = new TenantIsolationProperties();
        properties.setOidcTenantClaim("tenant_id");
        TenantStore tenantStore = mock(TenantStore.class);
        TenantIsolationValidator validator = new TenantIsolationValidator(properties, mock(JdbcTemplate.class));
        TenantScopeService scopeService = new TenantScopeService(tenantStore, properties, validator);

        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "alice", "tenant_id", "acme")
        );
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of());

        assertThat(scopeService.resolveTenantId(auth)).contains("acme");
        assertThat(scopeService.isPathVisible("root.tenant.acme.platform.devices.x", auth)).isTrue();
        assertThat(scopeService.isPathVisible("root.tenant.other.platform.devices.x", auth)).isFalse();
    }

    @Test
    void blankClaimFallsBackToUserTenantAssignment() {
        TenantIsolationProperties properties = new TenantIsolationProperties();
        properties.setOidcTenantClaim("tenant_id");
        TenantStore tenantStore = mock(TenantStore.class);
        when(tenantStore.findTenantIdForUser("bob")).thenReturn(java.util.Optional.of("plant1"));
        TenantIsolationValidator validator = new TenantIsolationValidator(properties, mock(JdbcTemplate.class));
        TenantScopeService scopeService = new TenantScopeService(tenantStore, properties, validator);

        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "bob")
        );
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of());
        // JwtAuthenticationToken name is usually sub
        assertThat(scopeService.resolveTenantId(auth)).isPresent();
    }
}
