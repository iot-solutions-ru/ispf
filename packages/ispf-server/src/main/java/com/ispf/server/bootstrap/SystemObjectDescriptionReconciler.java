package com.ispf.server.bootstrap;

import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps canonical English descriptions on platform system folder nodes.
 */
@Component
public class SystemObjectDescriptionReconciler {

    private final ObjectManager objectManager;

    public SystemObjectDescriptionReconciler(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @Transactional
    public void reconcile() {
        for (var entry : SystemObjectDescriptions.exactPaths().entrySet()) {
            apply(entry.getKey(), entry.getValue());
        }
        for (PlatformObject node : objectManager.tree().all()) {
            SystemObjectDescriptions.resolve(node.path()).ifPresent(entry -> apply(node.path(), entry));
        }
    }

    private void apply(String path, SystemObjectDescriptions.Entry entry) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            return;
        }
        String displayName = entry.displayName();
        PlatformObject existing = objectManager.require(path);
        if (displayName == null || displayName.isBlank()) {
            displayName = existing.displayName();
        }
        objectManager.updateInfo(path, displayName, entry.description());
    }
}
