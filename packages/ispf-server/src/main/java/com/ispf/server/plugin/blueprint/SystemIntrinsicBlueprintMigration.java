package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.SystemIntrinsicBlueprints;
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
public class SystemIntrinsicBlueprintMigration {

    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintEngine blueprintEngine;
    private final BlueprintPersistenceService blueprintPersistence;
    private final ObjectManager objectManager;

    public SystemIntrinsicBlueprintMigration(
            BlueprintRegistry blueprintRegistry,
            BlueprintEngine blueprintEngine,
            BlueprintPersistenceService blueprintPersistence,
            ObjectManager objectManager
    ) {
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintEngine = blueprintEngine;
        this.blueprintPersistence = blueprintPersistence;
        this.objectManager = objectManager;
    }

    @Transactional
    public void migrate() {
        patchBuiltinIntrinsicFlags();
        blueprintEngine.removeIntrinsicCatalogNodes();
        stripIntrinsicappliedBlueprintIds();
    }

    private void patchBuiltinIntrinsicFlags() {
        for (String name : SystemIntrinsicBlueprints.NAMES) {
            blueprintRegistry.findByName(name).ifPresent(existing -> {
                if (!existing.systemIntrinsic()) {
                    BlueprintDefinition updated = existing.withSystemIntrinsicFlag();
                    blueprintRegistry.update(updated);
                    blueprintPersistence.persist(updated, true);
                }
            });
        }
    }

    private void stripIntrinsicappliedBlueprintIds() {
        Set<String> intrinsicIds = new HashSet<>();
        for (BlueprintDefinition model : blueprintRegistry.all()) {
            if (SystemIntrinsicBlueprints.isIntrinsic(model)) {
                intrinsicIds.add(model.id());
            }
        }
        if (intrinsicIds.isEmpty()) {
            return;
        }
        for (PlatformObject node : objectManager.tree().all()) {
            List<String> remaining = new ArrayList<>();
            boolean changed = false;
            for (String modelId : node.appliedBlueprintIds()) {
                if (intrinsicIds.contains(modelId)) {
                    changed = true;
                } else {
                    remaining.add(modelId);
                }
            }
            if (changed) {
                node.setappliedBlueprintIds(remaining);
                objectManager.persistNodeTree(node.path());
            }
        }
    }
}
