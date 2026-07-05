package com.ispf.server.plugin.model;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.SystemIntrinsicModels;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Marks built-in 1:1 platform schemas as intrinsic, removes their catalog nodes, and strips applied-model metadata.
 */
@Component
public class SystemIntrinsicModelMigration {

    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;
    private final ModelPersistenceService modelPersistence;
    private final ObjectManager objectManager;

    public SystemIntrinsicModelMigration(
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ModelPersistenceService modelPersistence,
            ObjectManager objectManager
    ) {
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.modelPersistence = modelPersistence;
        this.objectManager = objectManager;
    }

    @Transactional
    public void migrate() {
        patchBuiltinIntrinsicFlags();
        modelEngine.removeIntrinsicCatalogNodes();
        stripIntrinsicAppliedModelIds();
    }

    private void patchBuiltinIntrinsicFlags() {
        for (String name : SystemIntrinsicModels.NAMES) {
            modelRegistry.findByName(name).ifPresent(existing -> {
                if (!existing.systemIntrinsic()) {
                    ModelDefinition updated = existing.withSystemIntrinsicFlag();
                    modelRegistry.update(updated);
                    modelPersistence.persist(updated, true);
                }
            });
        }
    }

    private void stripIntrinsicAppliedModelIds() {
        Set<String> intrinsicIds = new HashSet<>();
        for (ModelDefinition model : modelRegistry.all()) {
            if (SystemIntrinsicModels.isIntrinsic(model)) {
                intrinsicIds.add(model.id());
            }
        }
        if (intrinsicIds.isEmpty()) {
            return;
        }
        for (PlatformObject node : objectManager.tree().all()) {
            List<String> remaining = new ArrayList<>();
            boolean changed = false;
            for (String modelId : node.appliedModelIds()) {
                if (intrinsicIds.contains(modelId)) {
                    changed = true;
                } else {
                    remaining.add(modelId);
                }
            }
            if (changed) {
                node.setAppliedModelIds(remaining);
                objectManager.persistNodeTree(node.path());
            }
        }
    }
}
