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

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.cluster.enabled=true",
        "ispf.nats.replica-id=replica-a"
})
class DriverOwnershipServiceTest {

    private static final String DEVICE = "root.platform.devices.cluster-lock-test";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClusterProperties clusterProperties;

    @Autowired
    private DriverOwnershipService replicaA;

    private DriverOwnershipService replicaB;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM platform_driver_locks WHERE device_path = ?", DEVICE);
        replicaB = new DriverOwnershipService(
                jdbcTemplate,
                clusterProperties,
                new NatsProperties(false, "nats://localhost:4222", false, "replica-b", false, "ispf-automation", 24, "ispf-replica-")
        );
    }

    @Test
    void onlyOneReplicaHoldsLock() {
        assertTrue(replicaA.tryAcquire(DEVICE));
        assertFalse(replicaB.tryAcquire(DEVICE));
        assertEquals("replica-a", replicaA.findOwner(DEVICE).orElseThrow());
    }

    @Test
    void renewExtendsOwnership() {
        assertTrue(replicaA.tryAcquire(DEVICE));
        assertTrue(replicaA.renew(DEVICE));
        assertFalse(replicaB.tryAcquire(DEVICE));
    }

    @Test
    void releaseAllowsFailover() {
        assertTrue(replicaA.tryAcquire(DEVICE));
        replicaA.release(DEVICE);
        assertTrue(replicaB.tryAcquire(DEVICE));
        assertEquals("replica-b", replicaB.findOwner(DEVICE).orElseThrow());
    }

    @Test
    void expiredLockCanBeReclaimed() {
        assertTrue(replicaA.tryAcquire(DEVICE));
        jdbcTemplate.update(
                "UPDATE platform_driver_locks SET expires_at = ? WHERE device_path = ?",
                Timestamp.from(Instant.now().minusSeconds(5)),
                DEVICE
        );
        assertTrue(replicaB.tryAcquire(DEVICE));
        assertEquals("replica-b", replicaB.findOwner(DEVICE).orElseThrow());
    }
}
