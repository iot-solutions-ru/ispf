package com.ispf.server.object;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.PlatformObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectManagerTreePortTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformObject platformObject;

    @InjectMocks
    private ObjectManagerTreePort port;

    @Test
    void delegatesTreeAndRequire() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectManager.require("root.platform")).thenReturn(platformObject);

        assertThat(port.tree()).isSameAs(objectTree);
        assertThat(port.require("root.platform")).isSameAs(platformObject);

        verify(objectManager).tree();
        verify(objectManager).require("root.platform");
    }

    @Test
    void delegatesCreateDeletePersist() {
        when(objectManager.create(
                "root.platform.devices",
                "pump-1",
                ObjectType.DEVICE,
                "Pump",
                "desc",
                "device-v1"
        )).thenReturn(platformObject);

        assertThat(port.create(
                "root.platform.devices",
                "pump-1",
                ObjectType.DEVICE,
                "Pump",
                "desc",
                "device-v1"
        )).isSameAs(platformObject);

        port.delete("root.platform.devices.pump-1");
        port.persistNodeTree("root.platform.devices.pump-1");

        verify(objectManager).delete("root.platform.devices.pump-1");
        verify(objectManager).persistNodeTree("root.platform.devices.pump-1");
    }

    @Test
    void delegatesVariableOps() {
        port.setVariableValue("root.platform.devices.pump-1", "temperature", null);
        port.createVariable(
                "root.platform.devices.pump-1",
                "temperature",
                null,
                true,
                true,
                null,
                false,
                null
        );
        port.updateVariableHistory(
                "root.platform.devices.pump-1",
                "temperature",
                true,
                7,
                null,
                null,
                null,
                null
        );
        port.upsertFunction("root.platform.devices.pump-1", null);

        verify(objectManager).setVariableValue("root.platform.devices.pump-1", "temperature", null);
        verify(objectManager).createVariable(
                "root.platform.devices.pump-1",
                "temperature",
                null,
                true,
                true,
                null,
                false,
                null
        );
        verify(objectManager).updateVariableHistory(
                "root.platform.devices.pump-1",
                "temperature",
                true,
                7,
                null,
                null,
                null,
                null
        );
        verify(objectManager).upsertFunction("root.platform.devices.pump-1", null);
    }
}
