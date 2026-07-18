package com.ispf.server.object;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.persistence.ObjectEntityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectMetadataServiceTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectEntityMapper mapper;

    @InjectMocks
    private ObjectMetadataService metadataService;

    @Test
    void updateInfoDelegatesPersistAndPublish() {
        ObjectTree tree = new ObjectTree();
        PlatformObject node = new PlatformObject(
                "id-1",
                "root.platform.devices.pump",
                ObjectType.DEVICE,
                "Old",
                "desc",
                "device-v1",
                0
        );
        tree.register(node);
        when(objectManager.tree()).thenReturn(tree);
        when(mapper.auditDiff(any(), any())).thenReturn("{}");

        PlatformObject updated = metadataService.updateInfo(
                "root.platform.devices.pump",
                "Pump",
                "Water pump"
        );

        assertThat(updated.displayName()).isEqualTo("Pump");
        assertThat(updated.description()).isEqualTo("Water pump");
        verify(objectManager).assertExpectedRevision("root.platform.devices.pump");
        verify(objectManager).persistNodeConfig(eq(node), eq("UPDATE_INFO"), eq("metadata"), anyString());
        verify(objectManager).publishConfigChange(eq(ObjectChangeType.UPDATED), eq("root.platform.devices.pump"), anyLong());
    }
}
