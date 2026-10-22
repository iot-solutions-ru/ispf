package com.ispf.server.federation;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.EmbeddedServerPort;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederationUnbindPersistsProxyVariableDeleteTest {

    @Mock
    private FederationPeerStore peerStore;
    @Mock
    private FederationService federationService;
    @Mock
    private FederationProxyService federationProxyService;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private DriverRuntimeService driverRuntimeService;
    @Mock
    private EmbeddedServerPort embeddedServerPort;
    @Mock
    private Environment environment;

    @Test
    void unbindDeletesProxyVariablesWhileTheyAreStillOnTheNode() {
        String path = "root.devices.pump";
        PlatformObject node = new PlatformObject("pump", path, ObjectType.DEVICE, "pump", "", null);
        FederationProxyMetadata.applyTo(node, UUID.randomUUID(), "root.remote.pump");
        DataSchema stringValue = DataSchema.builder("stringValue").field("value", FieldType.STRING).build();
        String snapshot = "{\"type\":\"DEVICE\",\"displayName\":\"pump\",\"description\":\"\",\"driverWasRunning\":false}";
        node.addVariable(new Variable(
                FederationBindSnapshot.VAR_SNAPSHOT,
                stringValue,
                true,
                false,
                DataRecord.single(stringValue, Map.of("value", snapshot))
        ));
        when(objectManager.require(path)).thenReturn(node);

        List<String> deletedWhilePresent = new ArrayList<>();
        doAnswer(invocation -> {
            String name = invocation.getArgument(1);
            assertThat(node.getVariable(name))
                    .as("deleteVariable(%s) must run before clearFrom removes it from memory", name)
                    .isPresent();
            deletedWhilePresent.add(name);
            node.removeVariable(name);
            return null;
        }).when(objectManager).deleteVariable(eq(path), anyString());

        FederationBindService service = new FederationBindService(
                peerStore,
                federationService,
                federationProxyService,
                objectManager,
                driverRuntimeService,
                embeddedServerPort,
                environment,
                new ObjectMapper()
        );

        service.unbind(path);

        assertThat(deletedWhilePresent).contains(
                FederationProxyMetadata.VAR_PROXY,
                FederationProxyMetadata.VAR_PEER_ID,
                FederationProxyMetadata.VAR_REMOTE_PATH
        );
        verify(objectManager).deleteVariable(path, FederationProxyMetadata.VAR_PROXY);
        verify(objectManager).deleteVariable(path, FederationProxyMetadata.VAR_PEER_ID);
        verify(objectManager).deleteVariable(path, FederationProxyMetadata.VAR_REMOTE_PATH);
        assertThat(node.getVariable(FederationProxyMetadata.VAR_PROXY)).isEmpty();
        assertThat(node.getVariable(FederationProxyMetadata.VAR_PEER_ID)).isEmpty();
        assertThat(node.getVariable(FederationProxyMetadata.VAR_REMOTE_PATH)).isEmpty();
        assertThat(FederationProxyMetadata.isProxy(node)).isFalse();
    }
}
