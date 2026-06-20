package com.ispf.server.object;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Aligns in-memory object types with semantic {@link ObjectType} values after DB migration.
 */
@Component
public class SystemObjectTypeMigration {

    private final ObjectManager objectManager;

    public SystemObjectTypeMigration(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(50)
    @Transactional
    public void reconcileTypes() {
        for (PlatformObject node : objectManager.tree().all()) {
            SystemObjectTypeResolver.resolve(node.path(), node.templateId().orElse(null))
                    .filter(type -> node.type() != type)
                    .ifPresent(type -> objectManager.reconcileType(node.path(), type));
        }
    }
}
