package com.ispf.server.relational;

import com.ispf.server.audit.AuditChainStore;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.driver.DriverOwnershipService;
import com.ispf.server.persistence.entity.AuditEventEntity;
import com.ispf.server.spi.JdbcExpirySql;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ADR-0079 B1 lab gate: empty MySQL 8.4 → Flyway → CRUD smoke → concurrent audit → lock failover.
 *
 * <p>Skips when Docker is unavailable so default CI stays green without MySQL.
 */
@Tag("mysql-docker")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class MysqlFlywayBaselineTckTest {

    private static final String DEVICE = "root.platform.devices.mysql-tck-lock";

    @Test
    void emptyDatabaseAppliesBaselineAndSupportsB1Smoke() throws Exception {
        assumeTrue(MysqlTestSupport.dockerAvailable(), "Docker not available");

        try (MySQLContainer mysql = MysqlTestSupport.newContainer()) {
            mysql.start();

            MysqlDialect dialect = new MysqlDialect();
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(MysqlTestSupport.DRIVER_CLASS);
            dataSource.setUrl(mysql.getJdbcUrl());
            dataSource.setUsername(mysql.getUsername());
            dataSource.setPassword(mysql.getPassword());

            try (Connection connection = dataSource.getConnection()) {
                // Client != server: MariaDB Connector/J reports the MySQL product for a MySQL server.
                assertThat(RelationalDialectDetector.detectFromConnection(connection))
                        .isEqualTo(RelationalDbKind.MYSQL);
                assertThat(JdbcExpirySql.nowPlusSecondsExpression(connection.getMetaData().getDatabaseProductName()))
                        .isEqualTo(JdbcExpirySql.MYSQL_STYLE);
                assertThat(connection.getMetaData().getDatabaseProductVersion()).startsWith("8.4");
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(dialect.flywayLocations())
                    .load();
            flyway.migrate();
            assertThat(flyway.info().pending()).isEmpty();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertTable(jdbc, "object_nodes");
            assertTable(jdbc, "variable_samples");
            assertTable(jdbc, "platform_script_locks");
            assertTable(jdbc, "platform_driver_locks");
            assertTable(jdbc, "audit_events");
            assertTable(jdbc, "audit_event_chain");
            assertTable(jdbc, "blueprint_definitions");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM platform_roles WHERE built_in = " + dialect.booleanLiteral(true),
                    Integer.class
            )).isEqualTo(2);

            jdbc.update("""
                    INSERT INTO object_nodes (id, path, type, display_name, created_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                    """,
                    "n1", "root.platform.devices.mysql-tck", "DEVICE", "MySQL TCK"
            );
            // utf8mb4_0900_bin: paths that differ only by case are distinct (PostgreSQL equality).
            jdbc.update("""
                    INSERT INTO object_nodes (id, path, type, display_name, created_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                    """,
                    "n2", "root.platform.devices.MYSQL-TCK", "DEVICE", "MySQL TCK upper"
            );
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM object_nodes WHERE path = ?",
                    Integer.class,
                    "root.platform.devices.mysql-tck"
            )).isEqualTo(1);
            jdbc.update("""
                    INSERT INTO variable_samples (
                        object_path, variable_name, field_name, sampled_at, observed_at, value_double
                    ) VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), ?)
                    """,
                    "root.platform.devices.mysql-tck", "temperature", "value", 21.5
            );
            Integer sampleCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM variable_samples WHERE object_path = ?",
                    Integer.class,
                    "root.platform.devices.mysql-tck"
            );
            assertThat(sampleCount).isEqualTo(1);

            queuedJobSelectIsValid(jdbc, dialect);
            concurrentAuditChainHolds(jdbc, dialect);
            driverLockFailover(jdbc);
        }
    }

    private static void assertTable(JdbcTemplate jdbc, String table) {
        String collation = jdbc.queryForObject(
                "SELECT table_collation FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                String.class,
                table
        );
        assertThat(collation).as(table).isEqualTo("utf8mb4_0900_bin");
    }

    private static void queuedJobSelectIsValid(JdbcTemplate jdbc, MysqlDialect dialect) {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        List<String> jobs = tx.execute(status -> jdbc.queryForList(
                dialect.queuedPlatformJobSelectSql(), String.class, "QUEUED"));
        assertThat(jobs).isEmpty();
    }

    private static void concurrentAuditChainHolds(JdbcTemplate jdbc, MysqlDialect dialect) throws Exception {
        AuditChainStore store = new AuditChainStore(jdbc, dialect);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        int writers = 8;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                try {
                    start.await(30, TimeUnit.SECONDS);
                    tx.executeWithoutResult(status -> {
                        String id = "mysql-e-" + idx + "-" + UUID.randomUUID();
                        Instant at = Instant.parse("2026-11-22T12:00:00Z").plusSeconds(idx);
                        jdbc.update("""
                                INSERT INTO audit_events
                                (id, category, action, actor, target_type, target_id, details_json, occurred_at)
                                VALUES (?, 'object', 'update', 'tck', 'var', 'path', '{}', ?)
                                """, id, Timestamp.from(at));
                        AuditEventEntity event = new AuditEventEntity();
                        event.setId(id);
                        event.setCategory("object");
                        event.setAction("update");
                        event.setActor("tck");
                        event.setTargetType("var");
                        event.setTargetId("path");
                        event.setDetailsJson("{}");
                        event.setOccurredAt(at);
                        store.append(event);
                    });
                    ok.incrementAndGet();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(2, TimeUnit.MINUTES);
        }
        pool.shutdownNow();
        assertEquals(writers, ok.get());
        assertTrue(store.verify());
        Integer chainRows = jdbc.queryForObject("SELECT COUNT(*) FROM audit_event_chain", Integer.class);
        assertThat(chainRows).isEqualTo(writers);
    }

    private static void driverLockFailover(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM platform_driver_locks WHERE device_path = ?", DEVICE);
        // enabled + io profile → DRIVERS capability (ADR-0032)
        ClusterProperties cluster = new ClusterProperties(
                true, true, 60, 10, 15, 10, 30, true, 2, 8, 4, 6, 500,
                true, true, 500, true, "io", "", "all",
                true, 2000, 2, true, 1, 8, 50, 6, 500, 1800
        );

        DriverOwnershipService primary = new DriverOwnershipService(
                jdbc,
                cluster,
                nats("mysql-tck-a")
        );
        DriverOwnershipService standby = new DriverOwnershipService(
                jdbc,
                cluster,
                nats("mysql-tck-b")
        );

        assertTrue(primary.tryAcquire(DEVICE));
        assertFalse(standby.tryAcquire(DEVICE));
        assertEquals("mysql-tck-a", primary.findOwner(DEVICE).orElseThrow());

        jdbc.update(
                "UPDATE platform_driver_locks SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 SECOND) "
                        + "WHERE device_path = ?",
                DEVICE
        );
        assertTrue(standby.tryAcquire(DEVICE));
        assertEquals("mysql-tck-b", standby.findOwner(DEVICE).orElseThrow());
        assertFalse(primary.holdsLock(DEVICE));
    }

    private static NatsProperties nats(String replicaId) {
        return new NatsProperties(
                false, "nats://localhost:4222", false, replicaId, false,
                "ispf-automation", 24, "ispf-replica-", 65536, true, 2, 8, 50, 6, -1, 30
        );
    }
}
