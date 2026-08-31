package com.ispf.server.security;

import com.ispf.server.object.ObjectManager;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PlatformRolesJsonDeserializeTest {

    @Mock
    PlatformRoleStore roleStore;
    @Mock
    PlatformUserStore userStore;
    @Mock
    PlatformUserObjectTreeService objectTreeService;
    @Mock
    ObjectManager objectManager;
    @Mock
    TenantScopeService tenantScopeService;

    @Test
    void corruptRolesJsonReturnsEmptyList() throws Exception {
        PlatformRoleService roleService = new PlatformRoleService(
                roleStore,
                userStore,
                objectTreeService,
                objectManager,
                new ObjectMapper(),
                tenantScopeService
        );
        Method deserializeRoles = PlatformRoleService.class.getDeclaredMethod("deserializeRoles", String.class);
        deserializeRoles.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) deserializeRoles.invoke(roleService, "{not-valid-json");

        assertThat(roles).isEmpty();
    }
}
