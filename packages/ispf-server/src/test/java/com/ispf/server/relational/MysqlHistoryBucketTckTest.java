package com.ispf.server.relational;

import com.ispf.server.config.MysqlDatabasePreflight;
import com.ispf.server.event.EventHistoryRecordCounter;
import com.ispf.server.event.EventJournalRecord;
import com.ispf.server.event.JdbcEventJournalStore;
import com.ispf.server.history.JdbcVariableHistoryWriteStore;
import com.ispf.server.history.TimescaleHypertableInitializer;
import com.ispf.server.history.VariableHistoryWriteRecord;
import com.ispf.server.persistence.VariableSampleBucketAggregate;
import com.ispf.server.relational.store.VariableSampleBucketQuery;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ADR-0079 B2 (single-engine): JDBC history write + native bucket aggregate, and the batched journal
 * purge, on MySQL 8.4. Skips without Docker.
 */
@Tag("mysql-docker")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class MysqlHistoryBucketTckTest {

    private static final String DEVICE = "root.platform.devices.mysql-b2-sensor";

    @Test
    void writesSamplesAggregatesBucketsAndPurgesJournalInBatches() throws Exception {
        assumeTrue(MysqlTestSupport.dockerAvailable(), "Docker not available");

        try (MySQLContainer mysql = MysqlTestSupport.newContainer()) {
            mysql.start();

            MysqlDialect dialect = new MysqlDialect();
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(MysqlTestSupport.DRIVER_CLASS);
            dataSource.setUrl(mysql.getJdbcUrl());
            dataSource.setUsername(mysql.getUsername());
            dataSource.setPassword(mysql.getPassword());
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations(dialect.flywayLocations())
                    .load()
                    .migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            // The single-engine preflight passes on the lab cell (same queries as MysqlDatabasePreflightRunner).
            MysqlDatabasePreflight.assertReady(
                    jdbc.queryForObject("SELECT @@SESSION.transaction_isolation", String.class),
                    jdbc.queryForObject(
                            "SELECT default_collation_name FROM information_schema.schemata WHERE schema_name = DATABASE()",
                            String.class
                    )
            );

            Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
            JdbcVariableHistoryWriteStore writeStore = new JdbcVariableHistoryWriteStore(jdbc);
            writeStore.appendBatch(List.of(
                    sample("temp", t0, 10.0),
                    sample("temp", t0.plusSeconds(30), 20.0),
                    sample("temp", t0.plusMillis(59_999), 30.0),
                    sample("temp", t0.plusSeconds(90), 40.0),
                    new VariableHistoryWriteRecord(DEVICE, "temp", "value", t0.plusSeconds(100), t0.plusSeconds(100),
                            null, "text only", null),
                    sample("temp", t0.plusSeconds(200), 99.0)
            ));
            writeStore.appendOne(sample("shift", Instant.parse("2026-01-01T07:59:59Z"), 1.0));
            writeStore.appendOne(sample("shift", Instant.parse("2026-01-01T08:00:00Z"), 2.0));

            VariableSampleBucketQuery query = new VariableSampleBucketQuery(jdbc, dialect);
            List<VariableSampleBucketAggregate> buckets = query.aggregateBuckets(
                    DEVICE, "temp", "value", t0.minusSeconds(1), t0.plusSeconds(120), 60, 10);
            assertMinuteBuckets(buckets, t0);

            // max buckets keeps the newest, returned oldest-first.
            assertThat(query.aggregateBuckets(DEVICE, "temp", "value", t0.minusSeconds(1), t0.plusSeconds(120), 60, 1))
                    .extracting(VariableSampleBucketAggregate::getBucketStart)
                    .containsExactly(t0.plusSeconds(60));

            // 8h buckets are epoch (midnight) aligned, like the PostgreSQL and JVM paths.
            assertThat(query.aggregateBuckets(DEVICE, "shift", "value",
                    t0, Instant.parse("2026-01-02T00:00:00Z"), 8 * 3_600, 10))
                    .extracting(VariableSampleBucketAggregate::getBucketStart)
                    .containsExactly(t0, Instant.parse("2026-01-01T08:00:00Z"));

            // Neither a non-UTC JVM nor a non-UTC session time_zone moves a bucket.
            TimeZone previous = TimeZone.getDefault();
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            try {
                assertMinuteBuckets(query.aggregateBuckets(
                        DEVICE, "temp", "value", t0.minusSeconds(1), t0.plusSeconds(120), 60, 10), t0);
            } finally {
                TimeZone.setDefault(previous);
            }
            SingleConnectionDataSource session = new SingleConnectionDataSource(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword(), true);
            session.setDriverClassName(MysqlTestSupport.DRIVER_CLASS);
            try {
                JdbcTemplate sessionJdbc = new JdbcTemplate(session);
                sessionJdbc.execute("SET time_zone = '-07:00'");
                assertMinuteBuckets(new VariableSampleBucketQuery(sessionJdbc, dialect).aggregateBuckets(
                        DEVICE, "temp", "value", t0.minusSeconds(1), t0.plusSeconds(120), 60, 10), t0);
            } finally {
                session.destroy();
            }

            TimescaleHypertableInitializer timescale = mock(TimescaleHypertableInitializer.class);
            when(timescale.isEventHistoryTimescaleActive()).thenReturn(false);
            JdbcEventJournalStore journal = new JdbcEventJournalStore(
                    jdbc,
                    mock(EventHistoryRecordCounter.class),
                    timescale,
                    dialect,
                    new DataSourceTransactionManager(dataSource)
            );
            Instant cutoff = Instant.parse("2026-06-01T00:00:00Z");
            appendEvents(journal, DEVICE, 12_345, cutoff.minusSeconds(86_400));
            appendEvents(journal, DEVICE + ".child", 7, cutoff.minusSeconds(3_600));
            appendEvents(journal, "root.platform.devices.other", 11, cutoff.minusSeconds(3_600));
            appendEvents(journal, DEVICE, 3, cutoff.plusSeconds(60));

            // Scoped: the object and its children only, in DELETE … LIMIT 5000 batches.
            assertThat(journal.deleteOlderThan(cutoff, DEVICE)).isEqualTo(12_352);
            assertThat(countEvents(jdbc)).isEqualTo(14);
            assertThat(journal.queryRecent(DEVICE, 10)).hasSize(3);

            assertThat(journal.deleteOlderThan(cutoff, null)).isEqualTo(11);
            assertThat(countEvents(jdbc)).isEqualTo(3);
        }
    }

    private static void assertMinuteBuckets(List<VariableSampleBucketAggregate> buckets, Instant t0) {
        // 00:00:59.999999 truncates into the first minute; text-only and out-of-range rows are excluded.
        assertThat(buckets).extracting(VariableSampleBucketAggregate::getBucketStart)
                .containsExactly(t0, t0.plusSeconds(60));
        assertThat(buckets).extracting(VariableSampleBucketAggregate::getSampleCount).containsExactly(3L, 1L);
        assertThat(buckets).extracting(VariableSampleBucketAggregate::getAvgVal).containsExactly(20.0, 40.0);
        assertThat(buckets).extracting(VariableSampleBucketAggregate::getMinVal).containsExactly(10.0, 40.0);
        assertThat(buckets).extracting(VariableSampleBucketAggregate::getMaxVal).containsExactly(30.0, 40.0);
    }

    private static VariableHistoryWriteRecord sample(String variable, Instant at, double value) {
        return new VariableHistoryWriteRecord(DEVICE, variable, "value", at, at, value, null, null);
    }

    private static void appendEvents(JdbcEventJournalStore journal, String objectPath, int rows, Instant occurredAt) {
        List<EventJournalRecord> batch = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            batch.add(new EventJournalRecord(
                    UUID.randomUUID().toString(), objectPath, "b2-purge", "INFO", "{}", occurredAt.minusMillis(i)));
            if (batch.size() == 2_000) {
                journal.appendBatch(batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            journal.appendBatch(batch);
        }
    }

    private static long countEvents(JdbcTemplate jdbc) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM event_history", Long.class);
        return count == null ? 0 : count;
    }
}
