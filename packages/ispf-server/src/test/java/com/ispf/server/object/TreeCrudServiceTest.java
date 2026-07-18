package com.ispf.server.object;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreeCrudServiceTest {

    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectNodeRepository nodeRepository;
    @Mock
    private ObjectVariableRepository variableRepository;
    @Mock
    private ObjectEntityMapper mapper;
    @Mock
    private ObjectProvider<VisualGroupService> visualGroupService;

    private ObjectTree objectTree;
    private TreeCrudService treeCrudService;

    @BeforeEach
    void setUp() {
        objectTree = new ObjectTree();
        objectTree.register(new PlatformObject(
                "devices",
                "root.devices",
                ObjectType.DEVICES,
                "Devices",
                "",
                null
        ));
        lenient().when(objectManager.tree()).thenReturn(objectTree);
        treeCrudService = new TreeCrudService(
                objectManager,
                nodeRepository,
                variableRepository,
                mapper,
                visualGroupService
        );
    }

    @Test
    void createRegistersNodeAndPersists() {
        PlatformObject created = treeCrudService.create(
                "root.devices",
                "pump-1",
                ObjectType.DEVICE,
                "Pump",
                "desc",
                "device-v1"
        );

        assertThat(created.path()).isEqualTo("root.devices.pump-1");
        assertThat(objectTree.findByPath("root.devices.pump-1")).isPresent();
        verify(objectManager).persistNode(created);
        verify(objectManager).publish(any(ObjectChangeEvent.class));
    }

    @Test
    void createRejectsDuplicatePath() {
        treeCrudService.create(
                "root.devices",
                "pump-1",
                ObjectType.DEVICE,
                "Pump",
                null,
                null
        );

        assertThatThrownBy(() -> treeCrudService.create(
                "root.devices",
                "pump-1",
                ObjectType.DEVICE,
                "Pump",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deleteRemovesMemoryAndPublishes() {
        treeCrudService.create(
                "root.devices",
                "pump-1",
                ObjectType.DEVICE,
                "Pump",
                null,
                null
        );
        when(nodeRepository.findByPathPrefixOrderByPathLengthDesc("root.devices.pump-1"))
                .thenReturn(List.of());

        treeCrudService.delete("root.devices.pump-1");

        verify(objectManager).removePathFromMemoryIfPresent("root.devices.pump-1");
        verify(objectManager, atLeastOnce()).publish(any(ObjectChangeEvent.class));
    }

    @Test
    void deleteRootRejected() {
        assertThatThrownBy(() -> treeCrudService.delete("root"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(objectManager, never()).removePathFromMemoryIfPresent(any());
    }

    @Test
    void persistNodeTreePersistsNode() {
        PlatformObject node = treeCrudService.create(
                "root.devices",
                "pump-1",
                ObjectType.DEVICE,
                "Pump",
                null,
                null
        );

        treeCrudService.persistNodeTree("root.devices.pump-1");

        verify(objectManager, atLeastOnce()).persistNode(node);
        verify(objectManager, atLeastOnce()).publish(any(ObjectChangeEvent.class));
    }

    @Test
    void deleteMissingPathThrows() {
        when(nodeRepository.findByPathPrefixOrderByPathLengthDesc("root.missing"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> treeCrudService.delete("root.missing"))
                .isInstanceOf(ObjectNotFoundException.class);
    }
}
