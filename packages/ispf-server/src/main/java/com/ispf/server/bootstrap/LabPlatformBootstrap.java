package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.platform.ClusterPlatformBootstrapService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import com.ispf.server.report.ReportService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Phase 15: lab models and demo devices for multi-user collaboration exercises.
 */
@Component
public class LabPlatformBootstrap {

    private static final String DEVICES_ROOT = "root.platform.devices";

    private final LabBlueprintBootstrap labBlueprintBootstrap;
    private final HaystackBlueprintBootstrap haystackBlueprintBootstrap;
    private final BrickBlueprintBootstrap brickBlueprintBootstrap;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintApplicationService blueprintApplicationService;
    private final ObjectTemplateService objectTemplateService;
    private final ObjectManager objectManager;
    private final ReportService reportService;
    private final BootstrapProperties bootstrapProperties;
    private final ClusterPlatformBootstrapService clusterBootstrapService;

    public LabPlatformBootstrap(
            LabBlueprintBootstrap labBlueprintBootstrap,
            HaystackBlueprintBootstrap haystackBlueprintBootstrap,
            BrickBlueprintBootstrap brickBlueprintBootstrap,
            BlueprintRegistry blueprintRegistry,
            BlueprintApplicationService blueprintApplicationService,
            ObjectTemplateService objectTemplateService,
            ObjectManager objectManager,
            ReportService reportService,
            BootstrapProperties bootstrapProperties,
            ClusterPlatformBootstrapService clusterBootstrapService
    ) {
        this.labBlueprintBootstrap = labBlueprintBootstrap;
        this.haystackBlueprintBootstrap = haystackBlueprintBootstrap;
        this.brickBlueprintBootstrap = brickBlueprintBootstrap;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintApplicationService = blueprintApplicationService;
        this.objectTemplateService = objectTemplateService;
        this.objectManager = objectManager;
        this.reportService = reportService;
        this.bootstrapProperties = bootstrapProperties;
        this.clusterBootstrapService = clusterBootstrapService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 21)
    @Transactional
    public void onReady() {
        // Virtual driver Mixin Blueprints — required for agent create_virtual_device on prod (not demo fixtures).
        labBlueprintBootstrap.ensureLabModels();
        if (!bootstrapProperties.shouldSeedGeneralReferenceDemos() || !clusterBootstrapService.shouldRunFixtureBootstrap()) {
            return;
        }
        haystackBlueprintBootstrap.ensureHaystackModel();
        brickBlueprintBootstrap.ensureBrickModel();
        ensureLabDevice(
                "lab-userA-01",
                "Lab User A Device 01",
                "Collaboration lab device for user A"
        );
        ensureLabDevice(
                "lab-userB-01",
                "Lab User B Device 01",
                "Collaboration lab device for user B"
        );
        seedHaystackDemo(HaystackBlueprintBootstrap.DEMO_DEVICE_PATH);
        LabVirtualReports.deployAll(reportService);
    }

    private void ensureLabDevice(String name, String displayName, String description) {
        String path = DEVICES_ROOT + "." + name;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    DEVICES_ROOT,
                    name,
                    ObjectType.DEVICE,
                    displayName,
                    description,
                    LabBlueprintBootstrap.VIRTUAL_LAB_MODEL
            );
        } else {
            objectManager.reconcileType(path, ObjectType.DEVICE);
        }

        objectTemplateService.applyTemplate(path, LabBlueprintBootstrap.VIRTUAL_LAB_MODEL);
    }

    private void seedHaystackDemo(String path) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            return;
        }
        blueprintRegistry.findByName(HaystackBlueprintBootstrap.HAYSTACK_METADATA_MODEL).ifPresent(model -> {
            if (!objectManager.require(path).appliedBlueprintIds().contains(model.id())) {
                blueprintApplicationService.applyBlueprintWithRules(model, path, model.parameters());
            }
            DataSchema stringSchema = DataSchema.builder("stringValue")
                    .field("value", FieldType.STRING)
                    .build();
            objectManager.setVariableValue(
                    path,
                    "haystackKind",
                    DataRecord.single(stringSchema, Map.of("value", "equip"))
            );
            objectManager.setVariableValue(
                    path,
                    "haystackTags",
                    DataRecord.single(stringSchema, Map.of("value", HaystackBlueprintBootstrap.DEMO_HAYSTACK_TAGS))
            );
            objectManager.setVariableValue(
                    path,
                    "haystackRef",
                    DataRecord.single(stringSchema, Map.of("value", HaystackBlueprintBootstrap.DEMO_HAYSTACK_REF))
            );
            objectManager.persistNodeTree(path);
        });
        blueprintRegistry.findByName(BrickBlueprintBootstrap.BRICK_METADATA_MODEL).ifPresent(model -> {
            if (!objectManager.require(path).appliedBlueprintIds().contains(model.id())) {
                blueprintApplicationService.applyBlueprintWithRules(model, path, model.parameters());
            }
            DataSchema stringSchema = DataSchema.builder("stringValue")
                    .field("value", FieldType.STRING)
                    .build();
            objectManager.setVariableValue(
                    path,
                    "brickClass",
                    DataRecord.single(stringSchema, Map.of("value", BrickBlueprintBootstrap.DEMO_BRICK_CLASS))
            );
            objectManager.persistNodeTree(path);
        });
    }
}
