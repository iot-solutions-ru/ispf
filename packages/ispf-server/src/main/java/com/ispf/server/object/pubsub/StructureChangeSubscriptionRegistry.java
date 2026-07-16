package com.ispf.server.object.pubsub;

import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.cluster.NatsEventBridge;
import org.springframework.stereotype.Service;

/**
 * ADR-0024: demand-driven publication for structural object changes.
 */
@Service
public class StructureChangeSubscriptionRegistry {

    private final ObjectWebSocketPathInterestRegistry webSocketPathInterest;
    private final FederationExportInterestRegistry federationExportInterest;
    private final ClusterPathInterestStore clusterPathInterest;
    private final NatsEventBridge natsEventBridge;

    public StructureChangeSubscriptionRegistry(
            ObjectWebSocketPathInterestRegistry webSocketPathInterest,
            FederationExportInterestRegistry federationExportInterest,
            ClusterPathInterestStore clusterPathInterest,
            NatsEventBridge natsEventBridge
    ) {
        this.webSocketPathInterest = webSocketPathInterest;
        this.federationExportInterest = federationExportInterest;
        this.clusterPathInterest = clusterPathInterest;
        this.natsEventBridge = natsEventBridge;
    }

    public StructureChangeInterest interest(ObjectChangeType type, String path) {
        if (path == null || path.isBlank()) {
            return StructureChangeInterest.NONE;
        }
        boolean uiRefresh = webSocketPathInterest.hasPathInterest(path)
                || clusterPathInterest.hasPathInterest(path)
                || federationExportInterest.hasPathInterest(path);
        boolean platformMaintenance = true;
        boolean federationExport = federationExportInterest.hasPathInterest(path);
        boolean natsBridge = natsEventBridge.isEnabled();
        return new StructureChangeInterest(uiRefresh, platformMaintenance, federationExport, natsBridge);
    }
}
