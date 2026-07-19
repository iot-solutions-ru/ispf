package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IspfRolesTest {

    @Test
    void globalAdminIsNotTenantAdmin() {
        var auth = auth(IspfRoles.ADMIN);
        assertThat(IspfRoles.isGlobalAdmin(auth)).isTrue();
        assertThat(IspfRoles.isAdmin(auth)).isTrue();
        assertThat(IspfRoles.isTenantAdmin(auth)).isFalse();
        assertThat(IspfRoles.isConfigurator(auth)).isTrue();
    }

    @Test
    void tenantAdminIsConfiguratorButNotGlobalAdmin() {
        var auth = auth(IspfRoles.TENANT_ADMIN);
        assertThat(IspfRoles.isTenantAdmin(auth)).isTrue();
        assertThat(IspfRoles.isGlobalAdmin(auth)).isFalse();
        assertThat(IspfRoles.isConfigurator(auth)).isTrue();
    }

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(
                "u",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
