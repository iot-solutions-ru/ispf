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
import org.testcontainers.mysql.MySQLContainer;

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
 * ADR-0079 A-A lease gate on MySQL 8.4: the JDBC leader lease ({@code platform_leader_locks}) and the
 * driver lease ({@code platform_driver_locks}) between two replicas. Exclusive take, renewal, takeover
 * after expiry, hand-over on release, and racing first acquires that leave exactly one holder with no
 * deadlock. Both replicas share this JVM; the live two-JVM run is {@code tools/mysql-b1-lab/gate-aa.ps1}.
 *
 * <p>Skips when Docker is unavailable so default CI stays green without MySQL.
 */
@Tag("mysql-docker")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class MysqlClusterLeaseTckTest {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final int RACES = 20;

    @Test
    void leasesStayExclusiveBetweenTwoReplicas() throws Exception {
        assumeTrue(MysqlTestSupport.dockerAvailable(), "Docker not available");

        try (MySQLContainer mysql = MysqlTestSupport.newContainer()) {
            mysql.start();

            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(MysqlTestSupport.DRIVER_CLASS);
            dataSource.setUrl(mysql.getJdbcUrl());
            dataSource.setUsername(mysql.getUsername());
            dataSource.setPassword(mysql.getPassword());
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations(new MysqlDialect().flywayLocations())
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
        String lock = "mysql-aa-leader";

        assertThat(replicaA.runIfLeader(lock, TTL, MysqlClusterLeaseTckTest::noop)).isTrue();
        // The follower's INSERT hits 1062, which must reach the lease as a data-integrity refusal.
        assertThat(replicaB.runIfLeader(lock, TTL, MysqlClusterLeaseTckTest::noop)).isFalse();
        assertThat(replicaA.tryAcquire(lock, TTL)).as("holder renews").isTrue();
        assertThat(replicaB.tryAcquire(lock, TTL)).isFalse();

        jdbc.update(
                "UPDATE platform_leader_locks SET expires_at = ? WHERE lock_name = ?",
                Timestamp.from(Instant.now().minusSeconds(5)),
                lock
        );
        assertThat(replicaB.runIfLeader(lock, TTL, MysqlClusterLeaseTckTest::noop)).as("takeover after expiry").isTrue();
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
            String lock = "mysql-aa-race-" + i;
            List<Boolean> won = race(() -> replicaA.tryAcquire(lock, TTL), () -> replicaB.tryAcquire(lock, TTL));
            assertThat(won).as(lock).containsExactlyInAnyOrder(true, false);
            assertThat(holders(jdbc, lock)).as(lock).hasSize(1);
        }
    }

    private static void racingDriverAcquiresLeaveOneOwner(JdbcTemplate jdbc) throws Exception {
        DriverOwnershipService replicaA = new DriverOwnershipService(jdbc, cluster(), nats("mysql-aa-a"));
        DriverOwnershipService replicaB = new DriverOwnershipService(jdbc, cluster(), nats("mysql-aa-b"));
        for (int i = 0; i < RACES; i++) {
            String device = "root.platform.devices.mysql-aa-race-" + i;
            List<Boolean> won = race(() -> replicaA.tryAcquire(device), () -> replicaB.tryAcquire(device));
            assertThat(won).as(device).containsExactlyInAnyOrder(true, false);
            String winner = won.get(0) ? "mysql-aa-a" : "mysql-aa-b";
            assertThat(replicaA.findOwner(device)).as(device).contains(winner);
            assertThat(replicaB.findOwner(device)).as(device).contains(winner);
        }
    }

    /** Starts both calls on one latch; a deadlock or any other SQL error fails the test. */
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
                results.add(future.get(1, TimeUnit.MINUTES));
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
        // enabled + io profile → DRIVERS capability (ADR-0032)
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
