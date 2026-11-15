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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.cluster.enabled=true",
        "ispf.cluster.replica-profile=io",
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
                new NatsProperties(false, "nats://localhost:4222", false, "replica-b", false, "ispf-automation", 24, "ispf-replica-", 65536, true, 2, 8, 50, 6, -1, 30)
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

    @Test
    void tryAcquireSetsPositiveFencingEpoch() {
        assertTrue(replicaA.tryAcquire(DEVICE));
        assertEquals(1L, replicaA.currentEpochIfHolder(DEVICE));
        assertEquals(0L, replicaB.currentEpochIfHolder(DEVICE));
    }

    @Test
    void reclaimBumpsFencingEpoch() {
        assertTrue(replicaA.tryAcquire(DEVICE));
        long epochA = replicaA.currentEpochIfHolder(DEVICE);
        jdbcTemplate.update(
                "UPDATE platform_driver_locks SET expires_at = ? WHERE device_path = ?",
                Timestamp.from(Instant.now().minusSeconds(5)),
                DEVICE
        );
        assertTrue(replicaB.tryAcquire(DEVICE));
        long epochB = replicaB.currentEpochIfHolder(DEVICE);
        assertTrue(epochB > epochA, "steal must bump fencing epoch");
        assertEquals(0L, replicaA.currentEpochIfHolder(DEVICE));
    }

    @Test
    void renewBumpsZeroEpochToOne() {
        jdbcTemplate.update(
                """
                        INSERT INTO platform_driver_locks (device_path, holder_id, expires_at, fencing_epoch)
                        VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '60' SECOND, 0)
                        """,
                DEVICE,
                "replica-a"
        );
        assertTrue(replicaA.renew(DEVICE));
        assertEquals(1L, replicaA.currentEpochIfHolder(DEVICE));
    }

    /**
     * ADR-0069 follow-up: JDBC lease exclusivity across the ownership lifecycle
     * (not a multi-node dual-I/O proof).
     */
    @Test
    void noDualOwnerAcrossLifecycle() {
        assertTrue(replicaA.tryAcquire(DEVICE));
        assertAtMostOneActiveOwner();
        assertFalse(replicaB.tryAcquire(DEVICE));
        assertAtMostOneActiveOwner();

        jdbcTemplate.update(
                "UPDATE platform_driver_locks SET expires_at = ? WHERE device_path = ?",
                Timestamp.from(Instant.now().minusSeconds(5)),
                DEVICE
        );
        assertTrue(replicaB.tryAcquire(DEVICE));
        assertAtMostOneActiveOwner();
        assertFalse(replicaA.renew(DEVICE));
        assertEquals("replica-b", replicaB.findOwner(DEVICE).orElseThrow());

        replicaB.release(DEVICE);
        assertAtMostOneActiveOwner();
        assertTrue(replicaA.tryAcquire(DEVICE));
        assertAtMostOneActiveOwner();
    }

    @Test
    void concurrentAcquireYieldsSingleOwner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                jdbcTemplate.update("DELETE FROM platform_driver_locks WHERE device_path = ?", DEVICE);
                if (round % 2 == 1) {
                    jdbcTemplate.update(
                            """
                                    INSERT INTO platform_driver_locks (device_path, holder_id, expires_at, fencing_epoch)
                                    VALUES (?, ?, ?, 1)
                                    """,
                            DEVICE,
                            "stale-holder",
                            Timestamp.from(Instant.now().minusSeconds(5))
                    );
                }
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                AtomicInteger wins = new AtomicInteger();
                Future<Boolean> a = pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    boolean won = replicaA.tryAcquire(DEVICE);
                    if (won) {
                        wins.incrementAndGet();
                    }
                    return won;
                });
                Future<Boolean> b = pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    boolean won = replicaB.tryAcquire(DEVICE);
                    if (won) {
                        wins.incrementAndGet();
                    }
                    return won;
                });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                boolean aWon = a.get(5, TimeUnit.SECONDS);
                boolean bWon = b.get(5, TimeUnit.SECONDS);
                assertTrue(aWon ^ bWon, "exactly one replica must win round " + round);
                assertEquals(1, wins.get());
                assertAtMostOneActiveOwner();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertAtMostOneActiveOwner() {
        int activeRows = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM platform_driver_locks
                        WHERE device_path = ? AND expires_at > CURRENT_TIMESTAMP
                        """,
                Integer.class,
                DEVICE
        );
        assertTrue(activeRows <= 1, "active lease rows=" + activeRows);
        boolean aHolds = replicaA.holdsLock(DEVICE);
        boolean bHolds = replicaB.holdsLock(DEVICE);
        assertFalse(aHolds && bHolds, "both replicas report holdsLock");
    }
}
