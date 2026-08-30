package com.ispf.server.federation;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.function.FunctionInvokeAccessService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.VariableAclRequestContext;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederationTunnelLocalProxyServiceTest {

    private static final String OBJECT_PATH = "root.platform.devices.pump1";
    private static final String VARIABLE_NAME = "pressure";
    private static final Authentication AUTHENTICATION = new UsernamePasswordAuthenticationToken(
            "operator",
            "N/A",
            List.of(new SimpleGrantedAuthority("ROLE_operator"))
    );

    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectUiIconService objectUiIconService;
    @Mock
    private FunctionService functionService;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private VariableHistoryService variableHistoryService;
    @Mock
    private ObjectAccessService objectAccessService;
    @Mock
    private FunctionInvokeAccessService functionInvokeAccessService;
    @Mock
    private PlatformObject platformObject;

    private FederationTunnelLocalProxyService service;

    @BeforeEach
    void setUp() {
        VariableMemberAccessService variableAccessService =
                new VariableMemberAccessService(objectManager, objectAccessService);
        service = new FederationTunnelLocalProxyService(
                objectManager,
                objectUiIconService,
                functionService,
                dashboardService,
                variableHistoryService,
                variableAccessService,
                objectAccessService,
                functionInvokeAccessService,
                new ObjectMapper()
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesHistoryWhenMemberLacksVariableReadRole() {
        Variable variable = new Variable(
                VARIABLE_NAME,
                DataSchema.builder("numeric").field("value", FieldType.DOUBLE).build(),
                true,
                false,
                null,
                true,
                null,
                List.of("engineer"),
                List.of()
        );
        when(objectManager.require(OBJECT_PATH)).thenReturn(platformObject);
        when(platformObject.getVariable(VARIABLE_NAME)).thenReturn(Optional.of(variable));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"))
                .when(objectAccessService)
                .requireVariableRead(
                        OBJECT_PATH,
                        VARIABLE_NAME,
                        List.of("engineer"),
                        AUTHENTICATION
                );
        SecurityContextHolder.getContext().setAuthentication(AUTHENTICATION);

        var result = VariableAclRequestContext.callAsMember(() -> service.dispatch(
                "GET",
                "/api/v1/objects/by-path/variables/history",
                "path=" + OBJECT_PATH + "&name=" + VARIABLE_NAME,
                null
        ));

        assertThat(result.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.error()).isEqualTo("denied");
        verifyNoInteractions(variableHistoryService);
    }
}
