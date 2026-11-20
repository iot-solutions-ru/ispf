package com.ispf.server.relational;

import com.ispf.server.history.JdbcVariableHistoryWriteStore;
import com.ispf.server.history.VariableHistoryWriteRecord;
import com.ispf.server.persistence.VariableSampleBucketAggregate;
import com.ispf.server.relational.store.VariableSampleBucketQuery;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ADR-0078 B2 smoke: write samples and run DATE_BUCKET aggregate on MSSQL.
 * Skips without Docker.
 */
@Tag("mssql-docker")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class MssqlHistoryBucketTckTest {

    @Test
    void writesSamplesAndAggregatesBuckets() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        try (MSSQLServerContainer mssql = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense()
                .withStartupTimeout(Duration.ofMinutes(5))) {
            mssql.start();
            String jdbcUrl = MssqlTestSupport.prepareIspfDatabase(mssql);

            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(mssql.getDriverClassName());
            dataSource.setUrl(jdbcUrl);
            dataSource.setUsername(mssql.getUsername());
            dataSource.setPassword(mssql.getPassword());

            MssqlDialect dialect = new MssqlDialect();
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations(dialect.flywayLocations())
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            JdbcVariableHistoryWriteStore writeStore = new JdbcVariableHistoryWriteStore(jdbc);
            Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
            writeStore.appendBatch(List.of(
                    new VariableHistoryWriteRecord(
                            "root.device", "temp", "value", t0, t0, 10.0, null, null),
                    new VariableHistoryWriteRecord(
                            "root.device", "temp", "value", t0.plusSeconds(30), t0.plusSeconds(30), 20.0, null, null),
                    new VariableHistoryWriteRecord(
                            "root.device", "temp", "value", t0.plusSeconds(90), t0.plusSeconds(90), 30.0, null, null)
            ));

            VariableSampleBucketQuery query = new VariableSampleBucketQuery(jdbc, dialect);
            List<VariableSampleBucketAggregate> buckets = query.aggregateBuckets(
                    "root.device",
                    "temp",
                    "value",
                    t0.minusSeconds(1),
                    t0.plusSeconds(120),
                    60,
                    10
            );
            assertThat(buckets).isNotEmpty();
            assertThat(buckets.stream().mapToLong(VariableSampleBucketAggregate::getSampleCount).sum())
                    .isEqualTo(3);

            Integer uniqueIndexes = jdbc.queryForObject(
                    """
                            SELECT COUNT(*) FROM sys.indexes
                            WHERE name = 'uq_variable_samples_series_time'
                              AND object_id = OBJECT_ID('variable_samples')
                            """,
                    Integer.class
            );
            assertThat(uniqueIndexes).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.tables WHERE name = ?",
                    Integer.class,
                    "variable_sample_rollups"
            )).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.tables WHERE name = ?",
                    Integer.class,
                    "historian_spool_batches"
            )).isEqualTo(1);
        }
    }
}
