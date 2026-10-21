package com.ispf.server.plugin.blueprint;

import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Deleting a blueprint catalog node also drops its definition, so the same name can be created again.
 */
@Component
public class BlueprintCatalogDeleteListener {

    private final BlueprintEngine blueprintEngine;
    private final BlueprintPersistenceService blueprintPersistence;

    public BlueprintCatalogDeleteListener(
            BlueprintEngine blueprintEngine,
            BlueprintPersistenceService blueprintPersistence
    ) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintPersistence = blueprintPersistence;
    }

    @EventListener
    public void onObjectDeleted(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.DELETED) {
            return;
        }
        for (BlueprintDefinition model : blueprintEngine.removeDefinitionsForDeletedPath(event.path())) {
            blueprintPersistence.delete(model.id());
        }
    }
}
