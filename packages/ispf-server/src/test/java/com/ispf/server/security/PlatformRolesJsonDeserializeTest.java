package com.ispf.server.security;

import com.ispf.server.object.ObjectManager;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void corruptRolesJsonIsAnAccountError() throws Exception {
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

        assertThatThrownBy(() -> deserializeRoles.invoke(roleService, "{not-valid-json"))
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Corrupt roles JSON");
    }
}
