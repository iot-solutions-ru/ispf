package com.ispf.server.api;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.api.dto.VariableDto;
import com.ispf.server.federation.FederationProxyService;
import com.ispf.server.object.ObjectEditLeaseService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectControllerFederatedVariableAclTest {

    private static final String LOCAL_PATH = "root.platform.federation.edge.devices.pump";
    private static final Authentication OPERATOR = new UsernamePasswordAuthenticationToken(
            "operator",
            "N/A",
            List.of(new SimpleGrantedAuthority("ROLE_operator"))
    );
    private static final FederationProxyService.FederationProxyTarget TARGET =
            new FederationProxyService.FederationProxyTarget(
                    LOCAL_PATH,
                    UUID.randomUUID(),
                    "root.platform.devices.pump"
            );
    private static final DataSchema SCHEMA =
            DataSchema.builder("numeric").field("value", FieldType.DOUBLE).build();

    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectAccessService objectAccessService;
    @Mock
    private VariableMemberAccessService variableMemberAccessService;
    @Mock
    private TenantScopeService tenantScopeService;
    @Mock
    private TenantVirtualRootService tenantVirtualRootService;
    @Mock
    private FederationProxyService federationProxyService;
    @Mock
    private ObjectEditLeaseService editLeaseService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ObjectController controller;

    private PlatformObject localNode;

    @BeforeEach
    void setUp() {
        localNode = new PlatformObject(
                "pump",
                LOCAL_PATH,
                ObjectType.DEVICE,
                "Pump",
                "",
                null
        );
        when(tenantVirtualRootService.toCanonical(LOCAL_PATH, OPERATOR)).thenReturn(LOCAL_PATH);
        when(federationProxyService.resolve(LOCAL_PATH)).thenReturn(Optional.of(TARGET));
    }

    @Test
    void operatorListOmitsRemoteOnlyVariableWithoutLocalAclMetadata() {
        Variable mirrored = new Variable(
                "mirrored",
                SCHEMA,
                true,
                true,
                DataRecord.single(SCHEMA, Map.of("value", 10.0))
        );
        localNode.addVariable(mirrored);
        when(tenantScopeService.isPathVisible(LOCAL_PATH, OPERATOR)).thenReturn(true);
        ObjectTree tree = new ObjectTree();
        tree.register(localNode);
        when(objectManager.tree()).thenReturn(tree);
        when(variableMemberAccessService.canRead(LOCAL_PATH, "mirrored", OPERATOR)).thenReturn(true);
        var proxyResponse = objectMapper.valueToTree(List.of(
                variableDto("mirrored"),
                variableDto("remoteOnly")
        ));
        when(federationProxyService.proxyVariables(TARGET)).thenReturn(proxyResponse);

        List<VariableDto> result = controller.listVariables(LOCAL_PATH, OPERATOR);

        assertThat(result).extracting(VariableDto::name).containsExactly("mirrored");
    }

    @Test
    void operatorWriteRejectsRemoteOnlyVariableWithoutLocalAclMetadata() {
        DataRecord value = DataRecord.single(SCHEMA, Map.of("value", 12.0));
        when(objectManager.require(LOCAL_PATH)).thenReturn(localNode);

        assertThatThrownBy(() -> controller.setVariable(
                LOCAL_PATH,
                "remoteOnly",
                value,
                OPERATOR,
                HttpHeaders.EMPTY
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(federationProxyService, never()).proxyVariablePut(any(), anyString(), anyString());
    }

    private static VariableDto variableDto(String name) {
        return new VariableDto(
                name,
                DataRecord.single(SCHEMA, Map.of("value", 10.0)),
                true,
                true,
                null,
                false,
                null,
                "CHANGES_ONLY",
                false,
                "PERSISTENT",
                null,
                List.of(),
                List.of()
        );
    }
}
