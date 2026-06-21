package com.ispf.server.federation;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederationBindSnapshotTest {

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    @Mock
    private ObjectManager objectManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void restoreAndClearRestoresLocalMetadata() throws Exception {
        String path = "root.platform.devices.pump";
        PlatformObject node = new PlatformObject(
                "id-1",
                path,
                ObjectType.AGENT,
                "Remote name",
                "Remote desc",
                null
        );
        String json = objectMapper.writeValueAsString(Map.of(
                "type", "DEVICE",
                "displayName", "Local Pump",
                "description", "Local description",
                "driverWasRunning", true
        ));
        node.addVariable(new Variable(
                FederationBindSnapshot.VAR_SNAPSHOT,
                STRING_VALUE,
                false,
                false,
                null,
                DataRecord.single(STRING_VALUE, Map.of("value", json))
        ));

        when(objectManager.require(path)).thenReturn(node);

        Optional<FederationBindSnapshot.LocalState> restored = FederationBindSnapshot.restoreAndClear(
                objectManager,
                objectMapper,
                path
        );

        assertTrue(restored.isPresent());
        assertTrue(restored.get().driverWasRunning());
        assertEquals(ObjectType.DEVICE, restored.get().type());
        assertEquals("Local Pump", restored.get().displayName());
        verify(objectManager).reconcileType(path, ObjectType.DEVICE);
        verify(objectManager).updateInfo(path, "Local Pump", "Local description");
        verify(objectManager).deleteVariable(path, FederationBindSnapshot.VAR_SNAPSHOT);
    }
}
