package com.ispf.server.function;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.IspfRoles;
import com.ispf.server.datasource.DataSourceFunctionSupport;
import com.ispf.server.function.FunctionInvocationScope;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.RoleScopeAccessService;
import com.ispf.server.security.acl.ObjectAclStore;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FunctionInvokeAccessServiceTest {

    private static final String DATA_SOURCE = "root.platform.data-sources.demo";

    @Mock
    private ObjectManager objectManager;
    @Mock
    private com.ispf.core.object.ObjectTree objectTree;
    @Mock
    private ObjectAclStore aclStore;
    @Mock
    private RoleScopeAccessService roleScopeAccessService;

    @Mock
    private TenantScopeService tenantScopeService;

    private FunctionInvokeAccessService accessService;

    @BeforeEach
    void setUp() {
        lenient().when(roleScopeAccessService.isPathInRoleScope(anyString(), any())).thenReturn(true);
        accessService = new FunctionInvokeAccessService(
                new PrivilegedPlatformFunctionPolicy(objectManager),
                new ObjectAccessService(aclStore, roleScopeAccessService, tenantScopeService),
                objectManager
        );
        PlatformObject node = new PlatformObject("1", DATA_SOURCE, ObjectType.DATA_SOURCE, "Demo", "", "");
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(DATA_SOURCE)).thenReturn(java.util.Optional.of(node));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void operatorCannotInvokeExecuteQueryDirectly() {
        SecurityContextHolder.getContext().setAuthentication(operatorAuth());
        assertThatThrownBy(() -> accessService.guardScriptOnlyFunction(
                DATA_SOURCE,
                DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION_NAME,
                null
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("trusted script");
    }

    @Test
    void adminCanInvokeExecuteQueryDirectly() {
        SecurityContextHolder.getContext().setAuthentication(adminAuth());
        assertThatCode(() -> accessService.guardScriptOnlyFunction(
                DATA_SOURCE,
                DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION_NAME,
                null
        )).doesNotThrowAnyException();
    }

    @Test
    void developerCanInvokeExecuteQueryDirectly() {
        SecurityContextHolder.getContext().setAuthentication(developerAuth());
        assertThatCode(() -> accessService.guardScriptOnlyFunction(
                DATA_SOURCE,
                DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION_NAME,
                null
        )).doesNotThrowAnyException();
    }

    @Test
    void nestedInvokeAllowsOperatorContext() {
        SecurityContextHolder.getContext().setAuthentication(operatorAuth());
        FunctionInvocationScope.runNested(() ->
                accessService.guardScriptOnlyFunction(
                        DATA_SOURCE,
                        DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION_NAME,
                        null
                ));
    }

    private static UsernamePasswordAuthenticationToken operatorAuth() {
        return new UsernamePasswordAuthenticationToken(
                "operator",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + IspfRoles.OPERATOR))
        );
    }

    private static UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + IspfRoles.ADMIN))
        );
    }

    private static UsernamePasswordAuthenticationToken developerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "developer",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + IspfRoles.DEVELOPER))
        );
    }
}
