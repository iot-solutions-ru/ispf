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
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederationUnbindDriverRestartFailureTest {

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
    void unbindDoesNotSucceedWhenLocalDriverDoesNotRestart() {
        String path = "root.devices.pump";
        PlatformObject node = new PlatformObject("pump", path, ObjectType.DEVICE, "pump", "", null);
        FederationProxyMetadata.applyTo(node, UUID.randomUUID(), "root.remote.pump");
        DataSchema stringValue = DataSchema.builder("stringValue").field("value", FieldType.STRING).build();
        String snapshot = "{\"type\":\"DEVICE\",\"displayName\":\"pump\",\"description\":\"\",\"driverWasRunning\":true}";
        node.addVariable(new Variable(
                FederationBindSnapshot.VAR_SNAPSHOT,
                stringValue,
                true,
                false,
                DataRecord.single(stringValue, Map.of("value", snapshot))
        ));
        when(objectManager.require(path)).thenReturn(node);
        when(driverRuntimeService.start(path)).thenThrow(new IllegalStateException("driver down"));

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

        assertThatThrownBy(() -> service.unbind(path))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Local driver was not restarted after unbind")
                .hasMessageContaining("driver down");
    }
}
