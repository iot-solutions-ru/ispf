package com.ispf.server.object;

import com.ispf.server.object.pubsub.StructureChangeSubscriptionRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * ADR-0030: apply structural/config changes received from peer replicas into local RAM (PG reload).
 * <p>
 * Publishes {@link ObjectChangeEvent#replicaIngress()} WS refresh only when a live observer exists (ADR-0024).
 */
@Service
public class ClusterStructureReplicaApplier {

    private final ObjectManager objectManager;
    private final ApplicationEventPublisher eventPublisher;
    private final StructureChangeSubscriptionRegistry structureSubscriptionRegistry;

    public ClusterStructureReplicaApplier(
            ObjectManager objectManager,
            ApplicationEventPublisher eventPublisher,
            StructureChangeSubscriptionRegistry structureSubscriptionRegistry
    ) {
        this.objectManager = objectManager;
        this.eventPublisher = eventPublisher;
        this.structureSubscriptionRegistry = structureSubscriptionRegistry;
    }

    public void apply(ObjectChangeType type, String path, String variableName) {
        if (path == null || path.isBlank()) {
            return;
        }
        switch (type) {
            case CREATED, UPDATED -> objectManager.reloadPathFromDatabase(path);
            case DELETED -> objectManager.removePathFromMemoryIfPresent(path);
            case VARIABLE_UPDATED -> {
                if (variableName != null && !variableName.isBlank()) {
                    objectManager.syncVariableFromDatabase(path, variableName);
                }
            }
            default -> {
                return;
            }
        }
        if (structureSubscriptionRegistry.interest(type, path).liveObserver()) {
            eventPublisher.publishEvent(
                    variableName != null && !variableName.isBlank()
                            ? ObjectChangeEvent.structureReplicaIngress(type, path, variableName)
                            : ObjectChangeEvent.structureReplicaIngress(type, path)
            );
        }
    }
}
