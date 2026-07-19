package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleScopeAccessServiceTest {

    private final RoleScopeAccessService service = new RoleScopeAccessService();

    @Test
    void mesSupervisorAllowedOnMesPath() {
        var auth = new UsernamePasswordAuthenticationToken(
                "mes",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_mes-supervisor"))
        );
        assertThat(service.isPathInRoleScope("root.platform.mes.oee", auth)).isTrue();
        assertThat(service.isPathInRoleScope("root.platform.security.users", auth)).isFalse();
    }

    @Test
    void operatorReadonlyAllowedOnDashboardsOnly() {
        var auth = new UsernamePasswordAuthenticationToken(
                "op",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_operator-readonly"))
        );
        assertThat(service.isPathInRoleScope("root.platform.dashboards.main", auth)).isTrue();
        assertThat(service.isPathInRoleScope("root.platform.workflows.x", auth)).isFalse();
    }

    @Test
    void plainOperatorUnrestrictedByTemplateScopes() {
        var auth = new UsernamePasswordAuthenticationToken(
                "op",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_operator"))
        );
        assertThat(service.isPathInRoleScope("root.platform.security.users", auth)).isTrue();
    }
}
