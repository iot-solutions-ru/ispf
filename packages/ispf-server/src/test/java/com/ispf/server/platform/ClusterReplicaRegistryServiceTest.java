package com.ispf.server.platform;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.driver.DriverOwnershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "ispf.cluster.enabled=true",
        "ispf.cluster.replica-stale-seconds=30",
        "ispf.cluster.replica-profile=io",
        "ispf.nats.replica-id=cluster-node-a"
})
class ClusterReplicaRegistryServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClusterReplicaRegistryService registryService;

    @Autowired
    private DriverOwnershipService driverOwnershipService;

    @Autowired
    private ClusterProperties clusterProperties;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM platform_cluster_replicas");
        jdbcTemplate.update("DELETE FROM platform_driver_locks");
    }

    @Test
    void heartbeatRegistersSelfAndListsNodes() {
        registryService.recordHeartbeat();

        List<ClusterReplicaRegistryService.ClusterNode> nodes = registryService.listNodes();
        assertEquals(1, nodes.size());
        ClusterReplicaRegistryService.ClusterNode self = nodes.getFirst();
        assertEquals("cluster-node-a", self.replicaId());
        assertEquals("UP", self.status());
        assertTrue(self.self());
        assertTrue(self.httpPort() != null && self.httpPort() > 0);
    }

    @Test
    void mergesDriverLockHoldersWithoutHeartbeat() {
        String device = "root.platform.devices.cluster-registry-test";
        DriverOwnershipService peer = new DriverOwnershipService(
                jdbcTemplate,
                clusterProperties,
                new NatsProperties(false, "nats://localhost:4222", false, "cluster-node-b", false, "ispf-automation", 24, "ispf-replica-")
        );
        assertTrue(peer.tryAcquire(device));

        List<ClusterReplicaRegistryService.ClusterNode> nodes = registryService.listNodes();
        assertTrue(nodes.stream().anyMatch(node -> "cluster-node-b".equals(node.replicaId())));
        ClusterReplicaRegistryService.ClusterNode peerNode = nodes.stream()
                .filter(node -> "cluster-node-b".equals(node.replicaId()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, peerNode.heldDriverLocks());
        assertEquals("STALE", peerNode.status());
    }
}
