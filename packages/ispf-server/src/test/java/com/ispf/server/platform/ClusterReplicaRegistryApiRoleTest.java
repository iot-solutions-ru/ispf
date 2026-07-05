package com.ispf.server.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.cluster.enabled=true",
        "ispf.cluster.replica-role=api",
        "ispf.nats.replica-id=cluster-node-a"
})
class ClusterReplicaRegistryApiRoleTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClusterReplicaRegistryService registryService;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM platform_cluster_replicas");
        jdbcTemplate.update("DELETE FROM platform_driver_locks");
    }

    @Test
    void listsDriverLockCountsFromDatabaseEvenOnApiRole() {
        jdbcTemplate.update(
                """
                        INSERT INTO platform_driver_locks (device_path, holder_id, expires_at)
                        VALUES (?, ?, ?)
                        """,
                "root.platform.devices.api-role-lock-test",
                "replica-3",
                Timestamp.from(Instant.now().plusSeconds(300))
        );

        var peerNode = registryService.listNodes().stream()
                .filter(node -> "replica-3".equals(node.replicaId()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, peerNode.heldDriverLocks());
    }
}
