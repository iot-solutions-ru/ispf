package com.ispf.server.object;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Blueprint / demo / cluster fixture bootstrap collaborators extracted from {@link ObjectManager}
 * (ADR-0048 Wave 4) so ObjectManager no longer takes a sprawl of ObjectProviders in its constructor.
 */
@Service
public class ObjectTreeBootstrapFacade {

    private final BootstrapProperties bootstrapProperties;
    private final ObjectProvider<BlueprintBootstrap> blueprintBootstrap;
    private final ObjectProvider<BlueprintApplicationRunner> blueprintApplicationRunner;
    private final ObjectProvider<BlueprintPersistenceService> blueprintPersistence;
    private final ObjectProvider<BlueprintEngine> blueprintEngine;
    private final ObjectProvider<SystemIntrinsicBlueprintMigration> intrinsicBlueprintMigration;
    private final ObjectProvider<DemoFixtureBootstrap> demoFixtureBootstrap;
    private final ObjectProvider<PlatformReferenceBlueprintBootstrap> platformReferenceBlueprintBootstrap;
    private final ObjectProvider<MesBlueprintBootstrap> mesBlueprintBootstrap;
    private final ObjectProvider<ClusterPlatformBootstrapService> clusterBootstrapService;
    private final ObjectNodeRepository nodeRepository;
    private final ObjectVariableRepository variableRepository;

    public ObjectTreeBootstrapFacade(
            BootstrapProperties bootstrapProperties,
            ObjectProvider<BlueprintBootstrap> blueprintBootstrap,
            ObjectProvider<BlueprintApplicationRunner> blueprintApplicationRunner,
            ObjectProvider<BlueprintPersistenceService> blueprintPersistence,
            ObjectProvider<BlueprintEngine> blueprintEngine,
            ObjectProvider<SystemIntrinsicBlueprintMigration> intrinsicBlueprintMigration,
            ObjectProvider<DemoFixtureBootstrap> demoFixtureBootstrap,
            ObjectProvider<PlatformReferenceBlueprintBootstrap> platformReferenceBlueprintBootstrap,
            ObjectProvider<MesBlueprintBootstrap> mesBlueprintBootstrap,
            ObjectProvider<ClusterPlatformBootstrapService> clusterBootstrapService,
            ObjectNodeRepository nodeRepository,
            ObjectVariableRepository variableRepository
    ) {
        this.bootstrapProperties = bootstrapProperties;
        this.blueprintBootstrap = blueprintBootstrap;
        this.blueprintApplicationRunner = blueprintApplicationRunner;
        this.blueprintPersistence = blueprintPersistence;
        this.blueprintEngine = blueprintEngine;
        this.intrinsicBlueprintMigration = intrinsicBlueprintMigration;
        this.demoFixtureBootstrap = demoFixtureBootstrap;
        this.platformReferenceBlueprintBootstrap = platformReferenceBlueprintBootstrap;
        this.mesBlueprintBootstrap = mesBlueprintBootstrap;
        this.clusterBootstrapService = clusterBootstrapService;
        this.nodeRepository = nodeRepository;
        this.variableRepository = variableRepository;
    }

    public void prepareClusterFixtureRole() {
        clusterBootstrapService.ifAvailable(ClusterPlatformBootstrapService::prepareFixtureBootstrapRole);
    }

    /**
     * Runs catalog/blueprint/demo restore after the in-memory tree has been loaded or seeded.
     */
    public void runCatalogAndFixtures(ObjectManager objectManager) {
        blueprintBootstrap.getObject().ensureBuiltInBlueprints();
        blueprintPersistence.ifAvailable(BlueprintPersistenceService::restoreCustomBlueprints);
        intrinsicBlueprintMigration.ifAvailable(SystemIntrinsicBlueprintMigration::migrate);
        platformReferenceBlueprintBootstrap.ifAvailable(PlatformReferenceBlueprintBootstrap::ensureReferenceModels);
        if (bootstrapProperties.isMesCatalogEnabled()) {
            mesBlueprintBootstrap.ifAvailable(MesBlueprintBootstrap::ensureMesModels);
        }
        if (shouldApplyFixtureBlueprints()) {
            demoFixtureBootstrap.ifAvailable(demo ->
                    demo.seedDemos(blueprintApplicationRunner.getObject()));
        }
        blueprintEngine.ifAvailable(engine -> {
            engine.refreshBlueprintCatalogNodes();
            cleanupLegacyBlueprintCatalog(objectManager);
        });
        BlueprintApplicationRunner runner = blueprintApplicationRunner.getObject();
        runner.syncAllBlueprintBackedVariableMetadata();
        runner.restoreAttachments();
        runner.ensureDashboardDemoRules();
    }

    boolean shouldApplyFixtureBlueprints() {
        if (!bootstrapProperties.shouldSeedGeneralReferenceDemos()) {
            return false;
        }
        ClusterPlatformBootstrapService bootstrap = clusterBootstrapService.getIfAvailable();
        return bootstrap == null || bootstrap.shouldRunFixtureBootstrap();
    }

    void cleanupLegacyBlueprintCatalog(ObjectManager objectManager) {
        String legacyPrefix = BlueprintCatalogRoots.LEGACY + ".";
        List<String> legacyPaths = objectManager.tree().all().stream()
                .map(PlatformObject::path)
                .filter(path -> path.equals(BlueprintCatalogRoots.LEGACY) || path.startsWith(legacyPrefix))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String path : legacyPaths) {
            if (objectManager.tree().findByPath(path).isEmpty()) {
                continue;
            }
            objectManager.tree().delete(path);
            nodeRepository.findByPath(path).ifPresent(entity -> {
                variableRepository.deleteByObjectPath(path);
                nodeRepository.deleteById(entity.getId());
            });
        }
    }
}
