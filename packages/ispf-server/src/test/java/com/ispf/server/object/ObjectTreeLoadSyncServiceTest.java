package com.ispf.server.object;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectTreeLoadSyncServiceTest {

    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectNodeRepository nodeRepository;
    @Mock
    private ObjectVariableRepository variableRepository;
    @Mock
    private ObjectEntityMapper mapper;

    private ObjectTree objectTree;
    private ObjectTreeLoadSyncService loadSyncService;

    @BeforeEach
    void setUp() {
        objectTree = new ObjectTree();
        when(objectManager.tree()).thenReturn(objectTree);
        loadSyncService = new ObjectTreeLoadSyncService(
                objectManager,
                nodeRepository,
                variableRepository,
                mapper
        );
    }

    @Test
    void reloadPathMissingInDbRemovesFromMemory() {
        when(nodeRepository.findByPath("root.devices.pump-1")).thenReturn(Optional.empty());

        loadSyncService.reloadPathFromDatabase("root.devices.pump-1");

        verify(objectManager).removePathFromMemoryIfPresent("root.devices.pump-1");
    }

    @Test
    void reloadPathRegistersMissingRamNode() {
        ObjectNodeEntity entity = new ObjectNodeEntity();
        entity.setId("pump-id");
        entity.setPath("root.devices.pump-1");
        entity.setType(ObjectType.DEVICE);
        entity.setDisplayName("Pump");
        entity.setSortOrder(0);
        entity.setRevision(1L);
        when(nodeRepository.findByPath("root.devices.pump-1")).thenReturn(Optional.of(entity));
        when(variableRepository.findByObjectPathIn(List.of("root.devices.pump-1"))).thenReturn(List.of());
        when(mapper.readappliedBlueprintIds(any())).thenReturn(List.of());
        when(mapper.readEvents(any())).thenReturn(new com.ispf.core.object.EventDescriptor[0]);
        when(mapper.readFunctions(any())).thenReturn(new com.ispf.core.object.FunctionDescriptor[0]);

        loadSyncService.reloadPathFromDatabase("root.devices.pump-1");

        assertThat(objectTree.findByPath("root.devices.pump-1")).isPresent();
        verify(objectManager, never()).removePathFromMemoryIfPresent(any());
    }

    @Test
    void reloadFromDatabaseRunsBootstrapCallback() {
        when(nodeRepository.findAllByOrderByPathAsc()).thenReturn(List.of());
        AtomicBoolean bootstrapped = new AtomicBoolean(false);

        loadSyncService.reloadFromDatabase(() -> bootstrapped.set(true));

        assertThat(bootstrapped).isTrue();
    }

    @Test
    void syncVariableMissingRemovesRamVariable() {
        objectTree.register(new PlatformObject("pump", "root.devices.pump-1", ObjectType.DEVICE, "Pump", "", null));
        objectTree.require("root.devices.pump-1")
                .addVariable(new com.ispf.core.object.Variable(
                        "temperature",
                        com.ispf.core.model.DataSchema.builder("t").build(),
                        true,
                        true,
                        null
                ));
        when(variableRepository.findByObjectPathAndName("root.devices.pump-1", "temperature"))
                .thenReturn(Optional.empty());

        loadSyncService.syncVariableFromDatabase("root.devices.pump-1", "temperature");

        assertThat(objectTree.require("root.devices.pump-1").getVariable("temperature")).isEmpty();
    }
}
