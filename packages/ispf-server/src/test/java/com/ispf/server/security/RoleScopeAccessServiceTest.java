package com.ispf.server.security;

import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleScopeAccessServiceTest {

    @Mock
    private TenantScopeService tenantScopeService;

    private RoleScopeAccessService service;

    @BeforeEach
    void setUp() {
        service = new RoleScopeAccessService(tenantScopeService);
        lenient().when(tenantScopeService.resolveTenantId(any())).thenReturn(Optional.empty());
        lenient().when(tenantScopeService.tenantRootPrefix(any())).thenReturn(Optional.empty());
    }

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
