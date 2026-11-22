package com.ispf.server.relational;

import com.ispf.server.audit.AuditChainStore;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.driver.DriverOwnershipService;
import com.ispf.server.persistence.entity.AuditEventEntity;
import com.ispf.server.platform.PlatformJobService;
import com.ispf.server.spi.JdbcExpirySql;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.oracle.OracleContainer;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ADR-0080 B1 lab gate: empty Oracle Database Free schema → Flyway → Oracle semantics → job claim
 * with SKIP LOCKED → concurrent audit → lock failover.
 *
 * <p>Skips unless Docker and the lab ojdbc driver are both available.
 */
@Tag("oracle-docker")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class OracleFlywayBaselineTckTest {

    private static final String DEVICE = "root.platform.devices.oracle-tck-lock";

    @Test
    void emptySchemaAppliesBaselineAndSupportsB1Smoke() throws Exception {
        assumeTrue(OracleTestSupport.available(), "Docker or the Oracle lab driver is not available");

        try (OracleContainer oracle = OracleTestSupport.newContainer()) {
            oracle.start();

            OracleDialect dialect = new OracleDialect();
            DriverManagerDataSource raw = new DriverManagerDataSource();
            raw.setDriverClassName(OracleTestSupport.DRIVER_CLASS);
            raw.setUrl(oracle.getJdbcUrl());
            raw.setUsername(oracle.getUsername());
            raw.setPassword(oracle.getPassword());
            // Same wiring as RelationalConfiguration on an Oracle ISPF_DB.
            DataSource dataSource = new OracleUuidJdbcDataSource(raw);

            try (Connection connection = dataSource.getConnection()) {
                assertThat(RelationalDialectDetector.detectFromConnection(connection))
                        .isEqualTo(RelationalDbKind.ORACLE);
                assertThat(JdbcExpirySql.nowPlusSecondsExpression(connection.getMetaData().getDatabaseProductName()))
                        .isEqualTo(JdbcExpirySql.ORACLE_STYLE);
                assertThat(connection.getMetaData().getDatabaseProductVersion()).contains("Free").contains("Release 23.");
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(dialect.flywayLocations())
                    .load();
            flyway.migrate();
            assertThat(flyway.info().pending()).isEmpty();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            // Flyway quotes its table verbatim: lowercase on Oracle (PlatformStorageSchemaInitializer).
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = 1",
                    Integer.class
            )).isEqualTo(1);
            for (String table : List.of("object_nodes", "variable_samples", "platform_script_locks",
                    "platform_driver_locks", "audit_events", "audit_event_chain", "blueprint_definitions")) {
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM user_tables WHERE table_name = UPPER(?)", Integer.class, table
                )).as(table).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM platform_roles WHERE built_in = " + dialect.booleanLiteral(true),
                    Integer.class
            )).isEqualTo(2);

            objectPathsAreCaseSensitive(jdbc);
            emptyStringIsNull(jdbc);
            reservedWordColumnsRoundTrip(jdbc);
            jobClaimSkipsLockedRows(dataSource, dialect);
            concurrentAuditChainHolds(jdbc, dialect);
            driverLockFailover(jdbc);
        }
    }

    private static void objectPathsAreCaseSensitive(JdbcTemplate jdbc) {
        String insert = "INSERT INTO object_nodes (id, path, type, display_name) VALUES (?, ?, ?, ?)";
        jdbc.update(insert, "n1", "root.platform.devices.oracle-tck", "DEVICE", "Oracle TCK");
        jdbc.update(insert, "n2", "root.platform.devices.ORACLE-TCK", "DEVICE", "Oracle TCK upper");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM object_nodes WHERE path = ?", Integer.class, "root.platform.devices.oracle-tck"
        )).isEqualTo(1);
        jdbc.update("""
                INSERT INTO variable_samples (object_path, variable_name, field_name, sampled_at, observed_at, value_double)
                VALUES (?, ?, ?, LOCALTIMESTAMP, LOCALTIMESTAMP, ?)
                """, "root.platform.devices.oracle-tck", "temperature", "value", 21.5);
        assertThat(jdbc.queryForObject(
                "SELECT value_double FROM variable_samples WHERE object_path = ?", Double.class,
                "root.platform.devices.oracle-tck"
        )).isEqualTo(21.5);
    }

    /** Oracle stores '' as NULL: relaxed columns keep NULL, NOT NULL columns refuse '' (ORA-01400). */
    private static void emptyStringIsNull(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO platform_roles (name, display_name, description) VALUES (?, ?, ?)",
                "tck-empty", "tck-empty", "");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_roles WHERE name = 'tck-empty' AND description IS NULL", Integer.class
        )).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO object_nodes (id, path, type, display_name) VALUES (?, ?, ?, ?)",
                "n3", "root.platform.devices.oracle-empty", "DEVICE", ""
        )).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ORA-01400");
    }

    private static void reservedWordColumnsRoundTrip(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO alarm_shelves (id, object_path, event_name, shelved_by, shelved_at, "comment")
                VALUES (?, ?, ?, ?, LOCALTIMESTAMP, ?)
                """, "s1", "root.platform.devices.oracle-tck", "overheat", "tck", "maintenance window");
        assertThat(jdbc.queryForObject(
                "SELECT \"comment\" FROM alarm_shelves WHERE id = 's1'", String.class
        )).isEqualTo("maintenance window");
    }

    private static void jobClaimSkipsLockedRows(DataSource dataSource, OracleDialect dialect) throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        PlatformJobService jobs = new PlatformJobService(jdbc, new ObjectMapper(), cluster(), nats("oracle-tck-jobs"), dialect);
        UUID first = jobs.submit("tck", Map.of("n", 1), "tck");
        UUID second = jobs.submit("tck", Map.of("n", 2), "tck");
        jdbc.update("UPDATE platform_jobs SET priority = 5 WHERE job_id = ?", first);

        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch secondClaimed = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> holder = pool.submit(() -> tx.execute(status -> {
                UUID claimed = jobs.claimNextJob().orElseThrow().jobId();
                firstClaimed.countDown();
                // Keep the row lock until the second claimer has run.
                await(secondClaimed);
                return claimed;
            }));
            Future<UUID> skipper = pool.submit(() -> {
                await(firstClaimed);
                try {
                    return tx.execute(status -> jobs.claimNextJob().map(PlatformJobService.ClaimedJob::jobId).orElse(null));
                } finally {
                    secondClaimed.countDown();
                }
            });
            assertThat(holder.get(2, TimeUnit.MINUTES)).isEqualTo(first);
            assertThat(skipper.get(2, TimeUnit.MINUTES)).isEqualTo(second);
        } finally {
            pool.shutdownNow();
        }
        jobs.completeJob(first, Map.of("ok", true));
        assertThat(jobs.getJob(first).orElseThrow().status()).isEqualTo("COMPLETED");
        assertThat(jobs.getJob(second).orElseThrow().status()).isEqualTo("RUNNING");
    }

    private static void concurrentAuditChainHolds(JdbcTemplate jdbc, OracleDialect dialect) throws Exception {
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
                        String id = "oracle-e-" + idx + "-" + UUID.randomUUID();
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event_chain", Integer.class)).isEqualTo(writers);
    }

    private static void driverLockFailover(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM platform_driver_locks WHERE device_path = ?", DEVICE);
        DriverOwnershipService primary = new DriverOwnershipService(jdbc, cluster(), nats("oracle-tck-a"));
        DriverOwnershipService standby = new DriverOwnershipService(jdbc, cluster(), nats("oracle-tck-b"));

        assertTrue(primary.tryAcquire(DEVICE));
        assertFalse(standby.tryAcquire(DEVICE));
        assertEquals("oracle-tck-a", primary.findOwner(DEVICE).orElseThrow());

        jdbc.update(
                "UPDATE platform_driver_locks SET expires_at = CURRENT_TIMESTAMP - NUMTODSINTERVAL(5, 'SECOND') "
                        + "WHERE device_path = ?",
                DEVICE
        );
        assertTrue(standby.tryAcquire(DEVICE));
        assertEquals("oracle-tck-b", standby.findOwner(DEVICE).orElseThrow());
        assertFalse(primary.holdsLock(DEVICE));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for the other claimer");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    /** enabled + io profile → DRIVERS capability (ADR-0032). */
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
