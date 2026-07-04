package com.ispf.server.driver;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cluster failover scenarios for JDBC driver ownership (BL-137).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.cluster.enabled=true",
        "ispf.cluster.driver-lock-ttl-seconds=5",
        "ispf.nats.replica-id=cluster-test-a"
})
class ClusterFailoverIntegrationTest {

    private static final String DEVICE = "root.platform.devices.cluster-failover-test";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClusterProperties clusterProperties;

    @Autowired
    private DriverOwnershipService primaryReplica;

    private DriverOwnershipService standbyReplica;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM platform_driver_locks WHERE device_path = ?", DEVICE);
        standbyReplica = new DriverOwnershipService(
                jdbcTemplate,
                clusterProperties,
                new NatsProperties(false, "nats://localhost:4222", false, "cluster-test-b", false, "ispf-automation", 24, "ispf-replica-")
        );
    }

    @Test
    void ownerFailoverWithinLockTtl() {
        assertTrue(primaryReplica.tryAcquire(DEVICE));
        assertFalse(standbyReplica.tryAcquire(DEVICE));

        jdbcTemplate.update(
                "UPDATE platform_driver_locks SET expires_at = ? WHERE device_path = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                DEVICE
        );

        assertTrue(standbyReplica.tryAcquire(DEVICE));
        assertEquals("cluster-test-b", standbyReplica.findOwner(DEVICE).orElseThrow());
        assertFalse(primaryReplica.holdsLock(DEVICE));
    }

    @Test
    void clusterHealthReflectsHeldLocks() {
        int before = primaryReplica.countHeldLocks();
        assertTrue(primaryReplica.tryAcquire(DEVICE));
        assertEquals(before + 1, primaryReplica.countHeldLocks());
        assertTrue(primaryReplica.listHeldDevicePaths().contains(DEVICE));
    }
}
