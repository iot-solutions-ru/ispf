package com.ispf.server.platform;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ClusterReplicaHeartbeatScheduler {

    private final ClusterReplicaRegistryService registryService;

    public ClusterReplicaHeartbeatScheduler(ClusterReplicaRegistryService registryService) {
        this.registryService = registryService;
    }

    @PostConstruct
    void registerOnStartup() {
        registryService.recordHeartbeat();
    }

    @Scheduled(fixedDelayString = "${ispf.cluster.replica-heartbeat-ms:10000}")
    public void heartbeat() {
        registryService.recordHeartbeat();
    }
}
