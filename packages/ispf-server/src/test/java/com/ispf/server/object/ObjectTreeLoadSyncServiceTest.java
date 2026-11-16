package com.ispf.server.object;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.history.VariableHistoryService;
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
import java.util.Map;
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
    @Mock
    private VariableHistoryService historyService;

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
                mapper,
                historyService
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
    void loadFromDatabaseAppliesAuditFlagsOntoExistingStub() {
        PlatformObject stub = new PlatformObject(
                "hub-id",
                "root.platform.singleton-blueprints.audit-hub-v1",
                ObjectType.BLUEPRINT,
                "audit-hub-v1",
                "",
                null
        );
        objectTree.register(stub);
        assertThat(stub.bindingAuditEnabled()).isFalse();

        ObjectNodeEntity entity = new ObjectNodeEntity();
        entity.setId("hub-id");
        entity.setPath("root.platform.singleton-blueprints.audit-hub-v1");
        entity.setType(ObjectType.BLUEPRINT);
        entity.setDisplayName("audit-hub-v1");
        entity.setSortOrder(0);
        entity.setRevision(3L);
        entity.setBindingAuditEnabled(true);
        entity.setFunctionAuditEnabled(true);
        entity.setEventJournalEnabled(true);
        when(nodeRepository.findAllByOrderByPathAsc()).thenReturn(List.of(entity));
        when(mapper.readappliedBlueprintIds(any())).thenReturn(List.of());
        when(mapper.readBlueprintContributions(any())).thenReturn(Map.of());
        when(mapper.readEvents(any())).thenReturn(new com.ispf.core.object.EventDescriptor[0]);
        when(mapper.readFunctions(any())).thenReturn(new com.ispf.core.object.FunctionDescriptor[0]);

        loadSyncService.loadFromDatabase();

        PlatformObject loaded = objectTree.require("root.platform.singleton-blueprints.audit-hub-v1");
        assertThat(loaded.bindingAuditEnabled()).isTrue();
        assertThat(loaded.functionAuditEnabled()).isTrue();
        assertThat(loaded.eventJournalEnabled()).isTrue();
        assertThat(loaded.revision()).isEqualTo(3L);
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
        verify(historyService).forgetVariable("root.devices.pump-1", "temperature");
    }

    @Test
    void afterPathReloadedRefreshesRulesStateDependencyIndexAndJavaFunctions() {
        BindingRulesService rulesService = org.mockito.Mockito.mock(BindingRulesService.class);
        BindingDependencyIndex depIndex = org.mockito.Mockito.mock(BindingDependencyIndex.class);
        ObjectBindingStatePort statePort = org.mockito.Mockito.mock(ObjectBindingStatePort.class);
        com.ispf.plugin.blueprint.BlueprintEngine bpEngine =
                org.mockito.Mockito.mock(com.ispf.plugin.blueprint.BlueprintEngine.class);
        com.ispf.server.function.java.JavaFunctionRuntimeService javaRuntime =
                org.mockito.Mockito.mock(com.ispf.server.function.java.JavaFunctionRuntimeService.class);
        com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService tagCatalog =
                org.mockito.Mockito.mock(com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService.class);

        ObjectTreeLoadSyncService fullService = new ObjectTreeLoadSyncService(
                objectManager,
                nodeRepository,
                variableRepository,
                mapper,
                historyService,
                rulesService,
                depIndex,
                statePort,
                bpEngine,
                javaRuntime,
                tagCatalog
        );

        PlatformObject hub = new PlatformObject("h1", "root.devices.hub-1", ObjectType.CUSTOM, "Hub", "", null);
        hub.addVariable(new com.ispf.core.object.Variable(
                com.ispf.core.binding.BindingRulesConstants.RULES_VARIABLE,
                com.ispf.core.model.DataSchema.builder("rules").build(),
                true,
                true,
                null
        ));
        objectTree.register(hub);

        fullService.afterPathReloaded("root.devices.hub-1");

        verify(rulesService).invalidateRulesCache("root.devices.hub-1");
        verify(statePort).invalidateCache("root.devices.hub-1");
        verify(depIndex).rebuild("root.devices.hub-1");
        verify(javaRuntime).syncObjectFunctions(
                org.mockito.ArgumentMatchers.eq("root.devices.hub-1"),
                any()
        );
        verify(tagCatalog).invalidateCatalog();

        fullService.evictSubtreeCaches("root.devices.hub-1");

        verify(rulesService).forgetSubtree("root.devices.hub-1");
        verify(statePort).forgetSubtree("root.devices.hub-1");
        verify(depIndex).removeSubtree("root.devices.hub-1");
        verify(bpEngine).removeAttachmentsForDeletedSubtree("root.devices.hub-1");
        verify(javaRuntime).unregisterSubtree("root.devices.hub-1");
    }
}
