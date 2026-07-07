package com.ispf.server.security.acl;

import com.ispf.server.config.IspfRoles;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectAccessServiceVariableAclTest {

    private static final String PATH = "root.platform.devices.pump1";

    @Mock
    private ObjectAclStore aclStore;

    @Test
    void variableReadAllowedWhenRoleMatches() {
        ObjectAccessService service = new ObjectAccessService(aclStore);
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
        ObjectAccessService service = new ObjectAccessService(aclStore);
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
        ObjectAccessService service = new ObjectAccessService(aclStore);

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
        ObjectAccessService service = new ObjectAccessService(aclStore);
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
}
