package com.ispf.server.platform;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.driver.DriverOwnershipService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PlatformClusterHealthService {

    private final ClusterProperties clusterProperties;
    private final NatsProperties natsProperties;
    private final DriverOwnershipService driverOwnershipService;
    private final ClusterReplicaRegistryService replicaRegistryService;

    public PlatformClusterHealthService(
            ClusterProperties clusterProperties,
            NatsProperties natsProperties,
            DriverOwnershipService driverOwnershipService,
            ClusterReplicaRegistryService replicaRegistryService
    ) {
        this.clusterProperties = clusterProperties;
        this.natsProperties = natsProperties;
        this.driverOwnershipService = driverOwnershipService;
        this.replicaRegistryService = replicaRegistryService;
    }

    public ClusterHealth health() {
        List<ClusterReplicaRegistryService.ClusterNode> nodes = replicaRegistryService.listNodes();
        int upCount = (int) nodes.stream()
                .filter(node -> "UP".equals(node.status()))
                .count();
        return new ClusterHealth(
                clusterProperties.enabled(),
                clusterProperties.isDriverOwnershipActive(),
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
                Instant.now()
        );
    }

    public record ClusterHealth(
            boolean clusterEnabled,
            boolean driverOwnershipEnabled,
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
            Instant timestamp
    ) {
    }
}
