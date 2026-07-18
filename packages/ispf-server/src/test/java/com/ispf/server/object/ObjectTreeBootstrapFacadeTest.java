package com.ispf.server.object;

import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.server.application.reference.mes.MesBlueprintBootstrap;
import com.ispf.server.bootstrap.DemoFixtureBootstrap;
import com.ispf.server.bootstrap.PlatformReferenceBlueprintBootstrap;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.platform.ClusterPlatformBootstrapService;
import com.ispf.server.plugin.blueprint.BlueprintApplicationRunner;
import com.ispf.server.plugin.blueprint.BlueprintBootstrap;
import com.ispf.server.plugin.blueprint.BlueprintPersistenceService;
import com.ispf.server.plugin.blueprint.SystemIntrinsicBlueprintMigration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectTreeBootstrapFacadeTest {

    @Mock
    private BootstrapProperties bootstrapProperties;
    @Mock
    private ObjectProvider<BlueprintBootstrap> blueprintBootstrap;
    @Mock
    private ObjectProvider<BlueprintApplicationRunner> blueprintApplicationRunner;
    @Mock
    private ObjectProvider<BlueprintPersistenceService> blueprintPersistence;
    @Mock
    private ObjectProvider<BlueprintEngine> blueprintEngine;
    @Mock
    private ObjectProvider<SystemIntrinsicBlueprintMigration> intrinsicBlueprintMigration;
    @Mock
    private ObjectProvider<DemoFixtureBootstrap> demoFixtureBootstrap;
    @Mock
    private ObjectProvider<PlatformReferenceBlueprintBootstrap> platformReferenceBlueprintBootstrap;
    @Mock
    private ObjectProvider<MesBlueprintBootstrap> mesBlueprintBootstrap;
    @Mock
    private ObjectProvider<ClusterPlatformBootstrapService> clusterBootstrapService;
    @Mock
    private ObjectNodeRepository nodeRepository;
    @Mock
    private ObjectVariableRepository variableRepository;
    @Mock
    private BlueprintBootstrap blueprints;
    @Mock
    private BlueprintApplicationRunner runner;
    @Mock
    private ObjectManager objectManager;

    private ObjectTreeBootstrapFacade facade;

    @BeforeEach
    void setUp() {
        facade = new ObjectTreeBootstrapFacade(
                bootstrapProperties,
                blueprintBootstrap,
                blueprintApplicationRunner,
                blueprintPersistence,
                blueprintEngine,
                intrinsicBlueprintMigration,
                demoFixtureBootstrap,
                platformReferenceBlueprintBootstrap,
                mesBlueprintBootstrap,
                clusterBootstrapService,
                nodeRepository,
                variableRepository
        );
    }

    @Test
    void runCatalogAndFixturesInvokesBuiltInsAndRunner() {
        when(blueprintBootstrap.getObject()).thenReturn(blueprints);
        when(blueprintApplicationRunner.getObject()).thenReturn(runner);
        when(bootstrapProperties.isMesCatalogEnabled()).thenReturn(false);
        when(bootstrapProperties.shouldSeedGeneralReferenceDemos()).thenReturn(false);

        facade.runCatalogAndFixtures(objectManager);

        verify(blueprints).ensureBuiltInBlueprints();
        verify(runner).syncAllBlueprintBackedVariableMetadata();
        verify(runner).restoreAttachments();
        verify(runner).ensureDashboardDemoRules();
        verify(demoFixtureBootstrap, never()).ifAvailable(any());
    }

    @Test
    void prepareClusterFixtureRoleDelegates() {
        facade.prepareClusterFixtureRole();
        verify(clusterBootstrapService).ifAvailable(any());
    }
}
