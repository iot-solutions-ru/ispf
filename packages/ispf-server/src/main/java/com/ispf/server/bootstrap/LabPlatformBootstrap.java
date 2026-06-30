package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.plugin.model.ModelApplicationService;
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

    private final LabModelBootstrap labModelBootstrap;
    private final HaystackModelBootstrap haystackModelBootstrap;
    private final BrickModelBootstrap brickModelBootstrap;
    private final ModelRegistry modelRegistry;
    private final ModelApplicationService modelApplicationService;
    private final ObjectTemplateService objectTemplateService;
    private final ObjectManager objectManager;
    private final ReportService reportService;
    private final BootstrapProperties bootstrapProperties;

    public LabPlatformBootstrap(
            LabModelBootstrap labModelBootstrap,
            HaystackModelBootstrap haystackModelBootstrap,
            BrickModelBootstrap brickModelBootstrap,
            ModelRegistry modelRegistry,
            ModelApplicationService modelApplicationService,
            ObjectTemplateService objectTemplateService,
            ObjectManager objectManager,
            ReportService reportService,
            BootstrapProperties bootstrapProperties
    ) {
        this.labModelBootstrap = labModelBootstrap;
        this.haystackModelBootstrap = haystackModelBootstrap;
        this.brickModelBootstrap = brickModelBootstrap;
        this.modelRegistry = modelRegistry;
        this.modelApplicationService = modelApplicationService;
        this.objectTemplateService = objectTemplateService;
        this.objectManager = objectManager;
        this.reportService = reportService;
        this.bootstrapProperties = bootstrapProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 21)
    @Transactional
    public void onReady() {
        if (!bootstrapProperties.isFixturesEnabled()) {
            return;
        }
        labModelBootstrap.ensureLabModels();
        haystackModelBootstrap.ensureHaystackModel();
        brickModelBootstrap.ensureBrickModel();
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
        seedHaystackDemo(HaystackModelBootstrap.DEMO_DEVICE_PATH);
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
                    LabModelBootstrap.VIRTUAL_LAB_MODEL
            );
        } else {
            objectManager.reconcileType(path, ObjectType.DEVICE);
        }

        objectTemplateService.applyTemplate(path, LabModelBootstrap.VIRTUAL_LAB_MODEL);
    }

    private void seedHaystackDemo(String path) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            return;
        }
        modelRegistry.findByName(HaystackModelBootstrap.HAYSTACK_METADATA_MODEL).ifPresent(model -> {
            if (!objectManager.require(path).appliedModelIds().contains(model.id())) {
                modelApplicationService.applyModelWithRules(model, path, model.parameters());
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
                    DataRecord.single(stringSchema, Map.of("value", HaystackModelBootstrap.DEMO_HAYSTACK_TAGS))
            );
            objectManager.setVariableValue(
                    path,
                    "haystackRef",
                    DataRecord.single(stringSchema, Map.of("value", HaystackModelBootstrap.DEMO_HAYSTACK_REF))
            );
            objectManager.persistNodeTree(path);
        });
        modelRegistry.findByName(BrickModelBootstrap.BRICK_METADATA_MODEL).ifPresent(model -> {
            if (!objectManager.require(path).appliedModelIds().contains(model.id())) {
                modelApplicationService.applyModelWithRules(model, path, model.parameters());
            }
            DataSchema stringSchema = DataSchema.builder("stringValue")
                    .field("value", FieldType.STRING)
                    .build();
            objectManager.setVariableValue(
                    path,
                    "brickClass",
                    DataRecord.single(stringSchema, Map.of("value", BrickModelBootstrap.DEMO_BRICK_CLASS))
            );
            objectManager.persistNodeTree(path);
        });
    }
}
