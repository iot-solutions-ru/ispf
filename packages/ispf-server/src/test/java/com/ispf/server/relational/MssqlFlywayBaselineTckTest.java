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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.sql.Timestamp;
import java.time.Duration;
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
 * ADR-0078 Phase 2–3 lab gate: empty MSSQL → Flyway → CRUD smoke → job queue + SQL-binding triggers
 * → concurrent audit → lock failover.
 *
 * <p>Skips when Docker is unavailable so default CI stays green without MSSQL.
 */
@Tag("mssql-docker")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class MssqlFlywayBaselineTckTest {

    private static final String DEVICE = "root.platform.devices.mssql-tck-lock";

    @Test
    void emptyDatabaseAppliesBaselineAndSupportsB1Smoke() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        try (MSSQLServerContainer mssql = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense()
                .withStartupTimeout(Duration.ofMinutes(5))) {
            mssql.start();
            String jdbcUrl = MssqlTestSupport.prepareIspfDatabase(mssql);

            MssqlDialect dialect = new MssqlDialect();
            assertThat(dialect.nowPlusSecondsExpression()).isEqualTo(JdbcExpirySql.MSSQL_STYLE);

            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(mssql.getDriverClassName());
            dataSource.setUrl(jdbcUrl);
            dataSource.setUsername(mssql.getUsername());
            dataSource.setPassword(mssql.getPassword());

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

            jdbc.update("""
                    INSERT INTO object_nodes (id, path, type, display_name, created_at)
                    VALUES (?, ?, ?, ?, SYSUTCDATETIME())
                    """,
                    "n1", "root.platform.devices.mssql-tck", "DEVICE", "MSSQL TCK"
            );
            jdbc.update("""
                    INSERT INTO variable_samples (
                        object_path, variable_name, field_name, sampled_at, observed_at, value_double
                    ) VALUES (?, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME(), ?)
                    """,
                    "root.platform.devices.mssql-tck", "temperature", "value", 21.5
            );
            Integer sampleCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM variable_samples WHERE object_path = ?",
                    Integer.class,
                    "root.platform.devices.mssql-tck"
            );
            assertThat(sampleCount).isEqualTo(1);

            PlatformSqlPathsTck.jobQueueRoundTrip(jdbc, dialect, cluster(), nats("mssql-tck-jobs"));
            PlatformSqlPathsTck.functionSuccessTriggers(jdbc, dialect);
            concurrentAuditChainHolds(jdbc, dialect);
            driverLockFailover(jdbc);
        }
    }

    private static void assertTable(JdbcTemplate jdbc, String table) {
        // Under Latin1_General_CS_AS, INFORMATION_SCHEMA must be exact-case; prefer sys.tables.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys.tables WHERE name = ?",
                Integer.class,
                table
        );
        assertThat(count).as(table).isEqualTo(1);
    }

    private static void concurrentAuditChainHolds(JdbcTemplate jdbc, MssqlDialect dialect) throws Exception {
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
                        String id = "mssql-e-" + idx + "-" + UUID.randomUUID();
                        Instant at = Instant.parse("2026-11-19T12:00:00Z").plusSeconds(idx);
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
        ClusterProperties cluster = cluster();

        DriverOwnershipService primary = new DriverOwnershipService(
                jdbc,
                cluster,
                nats("mssql-tck-a")
        );
        DriverOwnershipService standby = new DriverOwnershipService(
                jdbc,
                cluster,
                nats("mssql-tck-b")
        );

        assertTrue(primary.tryAcquire(DEVICE));
        assertFalse(standby.tryAcquire(DEVICE));
        assertEquals("mssql-tck-a", primary.findOwner(DEVICE).orElseThrow());

        jdbc.update(
                "UPDATE platform_driver_locks SET expires_at = DATEADD(SECOND, -5, CURRENT_TIMESTAMP) WHERE device_path = ?",
                DEVICE
        );
        assertTrue(standby.tryAcquire(DEVICE));
        assertEquals("mssql-tck-b", standby.findOwner(DEVICE).orElseThrow());
        assertFalse(primary.holdsLock(DEVICE));
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
