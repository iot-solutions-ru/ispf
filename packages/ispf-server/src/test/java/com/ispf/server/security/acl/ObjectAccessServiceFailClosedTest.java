package com.ispf.server.security.acl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectAccessServiceFailClosedTest {

    @Mock
    private ObjectAclStore aclStore;

    @Test
    void emptyAclOnSecurityTreeDeniesOperator() {
        ObjectAccessService service = new ObjectAccessService(aclStore);
        when(aclStore.findEffectiveEntries("root.platform.security.users")).thenReturn(List.of());

        var auth = new UsernamePasswordAuthenticationToken(
                "operator",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_operator"))
        );

        assertThat(service.canRead("root.platform.security.users", auth)).isFalse();
    }

    @Test
    void emptyAclOnDeviceTreeStillFailOpen() {
        ObjectAccessService service = new ObjectAccessService(aclStore);
        when(aclStore.findEffectiveEntries("root.platform.devices.pump1")).thenReturn(List.of());

        var auth = new UsernamePasswordAuthenticationToken(
                "operator",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_operator"))
        );

        assertThat(service.canRead("root.platform.devices.pump1", auth)).isTrue();
    }

    @Test
    void failClosedPrefixHelper() {
        assertThat(ObjectAccessService.isFailClosedEmptyAclPath("root.platform.security")).isTrue();
        assertThat(ObjectAccessService.isFailClosedEmptyAclPath("root.platform.tenants.acme")).isTrue();
        assertThat(ObjectAccessService.isFailClosedEmptyAclPath("root.platform.devices.x")).isFalse();
    }
}
