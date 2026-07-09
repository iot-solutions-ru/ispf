package com.ispf.server.platform;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.config.ReplicaCapability;
import com.ispf.server.driver.DriverOwnershipService;
import com.ispf.server.platform.analytics.AnalyticsClusterWorkloadService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PlatformClusterHealthService {

    private final ClusterProperties clusterProperties;
    private final NatsProperties natsProperties;
    private final DriverOwnershipService driverOwnershipService;
    private final ClusterReplicaRegistryService replicaRegistryService;
    private final AnalyticsClusterWorkloadService analyticsClusterWorkloadService;

    public PlatformClusterHealthService(
            ClusterProperties clusterProperties,
            NatsProperties natsProperties,
            DriverOwnershipService driverOwnershipService,
            ClusterReplicaRegistryService replicaRegistryService,
            AnalyticsClusterWorkloadService analyticsClusterWorkloadService
    ) {
        this.clusterProperties = clusterProperties;
        this.natsProperties = natsProperties;
        this.driverOwnershipService = driverOwnershipService;
        this.replicaRegistryService = replicaRegistryService;
        this.analyticsClusterWorkloadService = analyticsClusterWorkloadService;
    }

    public ClusterHealth health() {
        List<ClusterReplicaRegistryService.ClusterNode> nodes = replicaRegistryService.listNodes();
        int upCount = (int) nodes.stream()
                .filter(node -> "UP".equals(node.status()))
                .count();
        return new ClusterHealth(
                clusterProperties.enabled(),
                clusterProperties.isDriverOwnershipActive(),
                clusterProperties.effectiveCapabilities().profile().externalName(),
                clusterProperties.effectiveCapabilities().profile().legacyRoleName(),
                clusterProperties.effectiveCapabilities().externalNames(),
                clusterProperties.isJobConsumerActive(),
                natsProperties.replicaId(),
                driverOwnershipService.countHeldLocks(),
                driverOwnershipService.listHeldDevicePaths(),
                clusterProperties.driverLockTtlSeconds(),
                natsProperties.enabled(),
                natsProperties.replicaEventsEnabled(),
                clusterProperties.isLiveVariableSyncActive(),
                clusterProperties.liveVariableSyncCoalesceMs(),
                clusterProperties.isClusterPathInterestActive(),
                nodes,
                upCount,
                nodes.size(),
                analyticsClusterWorkloadService.countUpAnalyticsReplicas(),
                analyticsClusterWorkloadService.isAnalyticsWorkloadActive(),
                clusterProperties.hasCapability(ReplicaCapability.ANALYTICS),
                Instant.now()
        );
    }

    public record ClusterHealth(
            boolean clusterEnabled,
            boolean driverOwnershipEnabled,
            String replicaProfile,
            String replicaRole,
            java.util.List<String> replicaCapabilities,
            boolean jobConsumerActive,
            String replicaId,
            int heldDriverLocks,
            List<String> heldDevicePaths,
            int driverLockTtlSeconds,
            boolean natsEnabled,
            boolean natsReplicaEventsEnabled,
            boolean liveVariableSyncEnabled,
            int liveVariableSyncCoalesceMs,
            boolean clusterPathInterestEnabled,
            List<ClusterReplicaRegistryService.ClusterNode> nodes,
            int nodesUp,
            int nodesTotal,
            int analyticsReplicasUp,
            boolean analyticsWorkloadActive,
            boolean localAnalyticsCapability,
            Instant timestamp
    ) {
    }
}
