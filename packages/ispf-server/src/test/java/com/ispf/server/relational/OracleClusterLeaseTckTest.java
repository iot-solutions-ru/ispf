package com.ispf.server.relational;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.driver.DriverOwnershipService;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.oracle.OracleContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ADR-0080 A-A lease gate on Oracle Database Free: JDBC leader lease
 * ({@code platform_leader_locks}) and driver lease ({@code platform_driver_locks}) between two
 * replicas. Exclusive take, renewal, takeover after expiry, hand-over on release, and racing first
 * acquires that leave exactly one holder. Both replicas share this JVM; the live two-JVM run is
 * {@code tools/oracle-b1-lab/gate-aa.ps1}.
 *
 * <p>Skips when Docker or the lab ojdbc is unavailable so default CI stays green without Oracle.
 */
@Tag("oracle-docker")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class OracleClusterLeaseTckTest {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final int RACES = 20;

    @Test
    void leasesStayExclusiveBetweenTwoReplicas() throws Exception {
        assumeTrue(OracleTestSupport.available(), "Docker or the Oracle lab driver is not available");

        try (OracleContainer oracle = OracleTestSupport.newContainer()) {
            oracle.start();

            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(OracleTestSupport.DRIVER_CLASS);
            dataSource.setUrl(oracle.getJdbcUrl());
            dataSource.setUsername(oracle.getUsername());
            dataSource.setPassword(oracle.getPassword());
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations(new OracleDialect().flywayLocations())
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);

            leaderLeaseTakeoverAndHandover(jdbc, transactionManager);
            racingLeaderAcquiresLeaveOneHolder(jdbc, transactionManager);
            racingDriverAcquiresLeaveOneOwner(jdbc);
        }
    }

    private static void leaderLeaseTakeoverAndHandover(JdbcTemplate jdbc, DataSourceTransactionManager tx) {
        PlatformLeaderLockService replicaA = new PlatformLeaderLockService(jdbc, tx);
        PlatformLeaderLockService replicaB = new PlatformLeaderLockService(jdbc, tx);
        String lock = "oracle-aa-leader";

        assertThat(replicaA.runIfLeader(lock, TTL, OracleClusterLeaseTckTest::noop)).isTrue();
        assertThat(replicaB.runIfLeader(lock, TTL, OracleClusterLeaseTckTest::noop)).isFalse();
        assertThat(replicaA.tryAcquire(lock, TTL)).as("holder renews").isTrue();
        assertThat(replicaB.tryAcquire(lock, TTL)).isFalse();

        jdbc.update(
                "UPDATE platform_leader_locks SET expires_at = ? WHERE lock_name = ?",
                Timestamp.from(Instant.now().minusSeconds(5)),
                lock
        );
        assertThat(replicaB.runIfLeader(lock, TTL, OracleClusterLeaseTckTest::noop)).as("takeover after expiry").isTrue();
        assertThat(replicaA.tryAcquire(lock, TTL)).as("old holder cannot take a live lease back").isFalse();
        assertThat(holders(jdbc, lock)).hasSize(1);

        replicaB.release(lock);
        assertThat(replicaA.tryAcquire(lock, TTL)).as("release hands over at once").isTrue();
        replicaA.release(lock);
    }

    private static void racingLeaderAcquiresLeaveOneHolder(JdbcTemplate jdbc, DataSourceTransactionManager tx)
            throws Exception {
        PlatformLeaderLockService replicaA = new PlatformLeaderLockService(jdbc, tx);
        PlatformLeaderLockService replicaB = new PlatformLeaderLockService(jdbc, tx);
        for (int i = 0; i < RACES; i++) {
            String lock = "oracle-aa-race-" + i;
            List<Boolean> won = race(() -> replicaA.tryAcquire(lock, TTL), () -> replicaB.tryAcquire(lock, TTL));
            assertThat(won).as(lock).containsExactlyInAnyOrder(true, false);
            assertThat(holders(jdbc, lock)).as(lock).hasSize(1);
        }
    }

    private static void racingDriverAcquiresLeaveOneOwner(JdbcTemplate jdbc) throws Exception {
        DriverOwnershipService replicaA = new DriverOwnershipService(jdbc, cluster(), nats("oracle-aa-a"));
        DriverOwnershipService replicaB = new DriverOwnershipService(jdbc, cluster(), nats("oracle-aa-b"));
        for (int i = 0; i < RACES; i++) {
            String device = "root.platform.devices.oracle-aa-race-" + i;
            List<Boolean> won = race(() -> replicaA.tryAcquire(device), () -> replicaB.tryAcquire(device));
            assertThat(won).as(device).containsExactlyInAnyOrder(true, false);
            String winner = won.get(0) ? "oracle-aa-a" : "oracle-aa-b";
            assertThat(replicaA.findOwner(device)).as(device).contains(winner);
            assertThat(replicaB.findOwner(device)).as(device).contains(winner);
        }
    }

    private static List<Boolean> race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> call : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return call.call();
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(2, TimeUnit.MINUTES));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<String> holders(JdbcTemplate jdbc, String lock) {
        return jdbc.queryForList("SELECT holder_id FROM platform_leader_locks WHERE lock_name = ?", String.class, lock);
    }

    private static void noop() {
    }

    private static ClusterProperties cluster() {
        return new ClusterProperties(
                true, true, 60, 10, 15, 10, 30, true, 2, 8, 4, 6, 500,
                true, true, 500, true, "io", "", "all",
                true, 2000, 2, true, 1, 8, 50, 6, 500, 1800
        );
    }

    private static NatsProperties nats(String replicaId) {
        return new NatsProperties(
                false, "nats://localhost:4222", false, replicaId, false,
                "ispf-automation", 24, "ispf-replica-", 65536, true, 2, 8, 50, 6, -1, 30
        );
    }
}
