package com.ispf.server.security.acl;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.security.RoleScopeAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectAccessServiceVariableAclTest {

    private static final String PATH = "root.platform.devices.pump1";

    @Mock
    private ObjectAclStore aclStore;

    @Mock
    private RoleScopeAccessService roleScopeAccessService;

    @Mock
    private TenantScopeService tenantScopeService;

    private ObjectAccessService service;

    @BeforeEach
    void setUp() {
        service = new ObjectAccessService(aclStore, roleScopeAccessService, tenantScopeService);
        lenient().when(roleScopeAccessService.isPathInRoleScope(eq(PATH), any())).thenReturn(true);
        lenient().when(tenantScopeService.tenantRootPrefix(any())).thenReturn(java.util.Optional.empty());
    }

    @Test
    void variableReadAllowedWhenRoleMatches() {
        when(aclStore.findEffectiveEntries(PATH)).thenReturn(List.of());

        var auth = new UsernamePasswordAuthenticationToken(
                "operator",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_operator"))
        );

        assertThat(service.canVariableRead(PATH, "pressure", List.of("operator"), auth)).isTrue();
    }

    @Test
    void variableReadDeniedWhenRoleMissing() {
        when(aclStore.findEffectiveEntries(PATH)).thenReturn(List.of());

        var auth = new UsernamePasswordAuthenticationToken(
                "operator",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_operator"))
        );

        assertThat(service.canVariableRead(PATH, "pressure", List.of("developer"), auth)).isFalse();
    }

    @Test
    void adminBypassesVariableRoleRestriction() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + IspfRoles.ADMIN))
        );

        assertThatCode(() -> service.requireVariableWrite(
                PATH,
                "pressure",
                List.of("developer"),
                auth
        )).doesNotThrowAnyException();
    }

    @Test
    void requireVariableWriteThrowsWhenDenied() {
        when(aclStore.findEffectiveEntries(PATH)).thenReturn(List.of());

        var auth = new UsernamePasswordAuthenticationToken(
                "operator",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_operator"))
        );

        assertThatThrownBy(() -> service.requireVariableWrite(
                PATH,
                "setpoint",
                List.of("developer"),
                auth
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Write access denied");
    }

    @Test
    void memberInvokeDeniedWhenInvokeRolesMissing() {
        when(aclStore.findEffectiveEntries(PATH)).thenReturn(List.of());
        var auth = new UsernamePasswordAuthenticationToken(
                "operator",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_operator"))
        );
        assertThatThrownBy(() -> service.requireMemberInvoke(
                PATH, "event", "trip", List.of("developer"), auth
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invoke access denied");
    }
}
