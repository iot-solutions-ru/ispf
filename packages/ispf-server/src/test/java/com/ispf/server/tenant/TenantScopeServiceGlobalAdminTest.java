package com.ispf.server.tenant;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.config.TenantIsolationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantScopeServiceGlobalAdminTest {

    @Test
    void onlyGlobalAdminBypassesTenantScope() {
        TenantIsolationProperties properties = new TenantIsolationProperties();
        TenantStore tenantStore = mock(TenantStore.class);
        when(tenantStore.findTenantIdForUser("ta")).thenReturn(Optional.of("acme"));
        TenantScopeService scope = new TenantScopeService(
                tenantStore,
                properties,
                new TenantIsolationValidator(properties, mock(JdbcTemplate.class))
        );

        var globalAdmin = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + IspfRoles.ADMIN))
        );
        assertThat(scope.resolveTenantId(globalAdmin)).isEmpty();
        assertThat(scope.isPathVisible("root.platform.devices", globalAdmin)).isTrue();

        var tenantAdmin = new UsernamePasswordAuthenticationToken(
                "ta",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + IspfRoles.TENANT_ADMIN))
        );
        assertThat(scope.resolveTenantId(tenantAdmin)).contains("acme");
        assertThat(scope.tenantRootPrefix(tenantAdmin)).contains("root.tenant.acme");
        assertThat(scope.isPathVisible("root.tenant.acme.platform.devices", tenantAdmin)).isTrue();
        // Sole-tenant virtual root: root.platform.* maps into the caller's platform subtree.
        assertThat(scope.isPathVisible("root.platform.devices", tenantAdmin)).isTrue();
        assertThat(scope.isPathVisible("root.tenant", tenantAdmin)).isFalse();
        assertThat(scope.isPathVisible("root.tenant.acme", tenantAdmin)).isFalse();
        assertThat(scope.isPathVisible("root.tenant.beta.platform.devices", tenantAdmin)).isFalse();

        scope.requireTenantAdminOf("acme", tenantAdmin);
        assertThatThrownBy(() -> scope.requireTenantAdminOf("beta", tenantAdmin))
                .isInstanceOf(ResponseStatusException.class);
    }
}
