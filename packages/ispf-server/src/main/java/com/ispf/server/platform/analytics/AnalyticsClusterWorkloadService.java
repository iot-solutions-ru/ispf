package com.ispf.server.platform.analytics;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.ReplicaCapability;
import com.ispf.server.config.ReplicaProfile;
import com.ispf.server.platform.ClusterReplicaRegistryService;
import org.springframework.stereotype.Service;

/**
 * Gates analytics materializer and heavy backfill to analytics-capable replicas (BL-207).
 */
@Service
public class AnalyticsClusterWorkloadService {

    private final ClusterProperties clusterProperties;
    private final ClusterReplicaRegistryService replicaRegistry;

    public AnalyticsClusterWorkloadService(
            ClusterProperties clusterProperties,
            ClusterReplicaRegistryService replicaRegistry
    ) {
        this.clusterProperties = clusterProperties;
        this.replicaRegistry = replicaRegistry;
    }

    /**
     * Whether this JVM should run rollup materializer / heavy analytics backfill.
     */
    public boolean isAnalyticsWorkloadActive() {
        if (clusterProperties.hasCapability(ReplicaCapability.ANALYTICS)) {
            return true;
        }
        if (!clusterProperties.enabled()) {
            return true;
        }
        if (replicaRegistry.hasUpReplicaWithCapability(ReplicaCapability.ANALYTICS)) {
            return false;
        }
        return clusterProperties.parsedReplicaProfile() == ReplicaProfile.UNIFIED;
    }

    public int countUpAnalyticsReplicas() {
        return replicaRegistry.countUpReplicasWithCapability(ReplicaCapability.ANALYTICS);
    }
}
