package com.ispf.server.object;

import com.ispf.server.config.ClusterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Keeps in-memory object trees aligned across cluster replicas by reloading structural
 * changes from the shared database when another replica publishes via NATS.
 */
@Component
public class ClusterObjectTreeReplicaSync {

    private static final Logger log = LoggerFactory.getLogger(ClusterObjectTreeReplicaSync.class);

    private final ClusterProperties clusterProperties;
    private final ObjectManager objectManager;

    public ClusterObjectTreeReplicaSync(ClusterProperties clusterProperties, ObjectManager objectManager) {
        this.clusterProperties = clusterProperties;
        this.objectManager = objectManager;
    }

    @EventListener
    @Order(50)
    public void onObjectChange(ObjectChangeEvent event) {
        if (!clusterProperties.enabled() || event.telemetry()) {
            return;
        }
        if (!objectManager.isInitialized()) {
            return;
        }
        switch (event.type()) {
            case CREATED, UPDATED -> {
                if (objectManager.tree().findByPath(event.path()).isEmpty()) {
                    objectManager.syncPathFromDatabase(event.path());
                }
            }
            case DELETED -> objectManager.removePathFromMemoryIfPresent(event.path());
            default -> {
            }
        }
    }
}
