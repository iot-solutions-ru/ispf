package com.ispf.server.bootstrap;

import com.ispf.core.object.ObjectType;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.object.ObjectManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 15: lab models and demo devices for multi-user collaboration exercises.
 */
@Component
public class LabPlatformBootstrap {

    private static final String DEVICES_ROOT = "root.platform.devices";

    private final LabModelBootstrap labModelBootstrap;
    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;
    private final ObjectManager objectManager;

    public LabPlatformBootstrap(
            LabModelBootstrap labModelBootstrap,
            ModelEngine modelEngine,
            ModelRegistry modelRegistry,
            ObjectManager objectManager
    ) {
        this.labModelBootstrap = labModelBootstrap;
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
        this.objectManager = objectManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 21)
    @Transactional
    public void onReady() {
        labModelBootstrap.ensureLabModels();
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

        modelRegistry.findByName(LabModelBootstrap.VIRTUAL_LAB_MODEL).ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
        modelRegistry.findByName(LabModelBootstrap.VIRTUAL_LAB_WAVES_SUM_MODEL).ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }
}
