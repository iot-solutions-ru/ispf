package com.ispf.server.relational;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.history.JdbcMssqlHistorianRollupMaterializer;
import com.ispf.server.history.JdbcVariableHistoryWriteStore;
import com.ispf.server.history.VariableHistoryWriteRecord;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Gate C2: empty-cursor seed, hour+day MERGE, exclusions, monotonic cursor.
 */
@Tag("mssql-docker")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class MssqlJdbcRollupMaterializerTckTest {

    @Test
    void materializeTickFromEmptyCursorWritesHourAndDayBuckets() throws Exception {
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

            Flyway.configure()
                    .dataSource(dataSource)
                    .locations(new MssqlDialect().flywayLocations())
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update(
                    """
                            INSERT INTO object_variables (object_path, name, schema_json, history_enabled, history_retention_days)
                            VALUES (?, ?, ?, 1, 90)
                            """,
                    "root.device", "temp", "{}"
            );
            jdbc.update(
                    """
                            INSERT INTO object_variables (object_path, name, schema_json, history_enabled, history_retention_days)
                            VALUES (?, ?, ?, 0, 90)
                            """,
                    "root.device", "disabled", "{}"
            );

            // Yesterday UTC so both hours sit in one closed day bucket (avoids midnight flake).
            Instant dayA = JdbcMssqlHistorianRollupMaterializer.floorToBucket(Instant.now(), 86_400)
                    .minus(Duration.ofDays(1));
            Instant hourA = dayA.plus(Duration.ofHours(2));
            Instant t0 = hourA.plus(Duration.ofMinutes(15));
            Instant t1 = hourA.plus(Duration.ofMinutes(45));
            Instant hourB = dayA.plus(Duration.ofHours(3));
            Instant t2 = hourB.plus(Duration.ofMinutes(10));

            JdbcVariableHistoryWriteStore writeStore = new JdbcVariableHistoryWriteStore(jdbc);
            List<VariableHistoryWriteRecord> records = new ArrayList<>();
            records.add(new VariableHistoryWriteRecord("root.device", "temp", "value", t0, t0, 10.0, null, null));
            records.add(new VariableHistoryWriteRecord("root.device", "temp", "value", t1, t1, 20.0, null, null));
            records.add(new VariableHistoryWriteRecord("root.device", "temp", "value", t2, t2, 30.0, null, null));
            // Exclusions
            records.add(new VariableHistoryWriteRecord(
                    "root.device", "temp", "$record", t0, t0, 99.0, null, null));
            records.add(new VariableHistoryWriteRecord(
                    "root.device", "disabled", "value", t0, t0, 50.0, null, null));
            records.add(new VariableHistoryWriteRecord(
                    "root.device", "temp", "value", t0.plusSeconds(1), t0.plusSeconds(1), null, "text", null));
            writeStore.appendBatch(records);

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM mssql_historian_rollup_cursor", Integer.class
            )).isZero();

            MssqlRelationalProperties mssqlProps = new MssqlRelationalProperties();
            mssqlProps.setSingleEngine(true);
            mssqlProps.setRollupMaterializerEnabled(true);
            mssqlProps.setMaxRetentionDays(90);
            mssqlProps.setRollupMaterializerChunkHours(6); // must align up to full day for 1d

            VariableHistoryProperties historyProps = new VariableHistoryProperties();
            historyProps.setEnabled(true);
            historyProps.setStore("jdbc");
            historyProps.setRetentionDays(30); // default < cap

            ClusterProperties cluster = mock(ClusterProperties.class);
            when(cluster.isSchedulerActive()).thenReturn(true);

            JdbcMssqlHistorianRollupMaterializer materializer = new JdbcMssqlHistorianRollupMaterializer(
                    jdbc,
                    mssqlProps,
                    historyProps,
                    cluster,
                    new MssqlDialect(),
                    new DataSourceTransactionManager(dataSource)
            );

            JdbcMssqlHistorianRollupMaterializer.TickResult tick = materializer.materializeTick();
            assertThat(tick.ran()).isTrue();

            Integer hourBuckets = jdbc.queryForObject(
                    """
                            SELECT COUNT(*) FROM variable_sample_rollups
                            WHERE object_path = ? AND variable_name = ? AND field_name = 'value'
                              AND bucket_seconds = 3600
                            """,
                    Integer.class,
                    "root.device", "temp"
            );
            assertThat(hourBuckets).isEqualTo(2);
            Double hourAvg = jdbc.query(
                    connection -> {
                        var statement = connection.prepareStatement(
                                """
                                        SELECT avg_val, sample_count FROM variable_sample_rollups
                                        WHERE object_path = ? AND variable_name = ? AND bucket_seconds = 3600
                                          AND bucket_start = ?
                                        """
                        );
                        statement.setString(1, "root.device");
                        statement.setString(2, "temp");
                        JdbcUtcTimestamps.bind(statement, 3, hourA);
                        return statement;
                    },
                    rs -> {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong("sample_count")).isEqualTo(2);
                        return rs.getDouble("avg_val");
                    }
            );
            assertThat(hourAvg).isEqualTo(15.0);

            Integer dayBuckets = jdbc.queryForObject(
                    """
                            SELECT COUNT(*) FROM variable_sample_rollups
                            WHERE object_path = ? AND variable_name = ? AND field_name = 'value'
                              AND bucket_seconds = 86400
                            """,
                    Integer.class,
                    "root.device", "temp"
            );
            assertThat(dayBuckets).isEqualTo(1);

            assertThat(jdbc.queryForObject(
                    """
                            SELECT COUNT(*) FROM variable_sample_rollups
                            WHERE field_name = '$record' OR variable_name = 'disabled'
                            """,
                    Integer.class
            )).isZero();

            Instant hourCovered = materializer.loadCoveredThrough(3_600).orElseThrow();
            Instant dayCovered = materializer.loadCoveredThrough(86_400).orElseThrow();
            Instant openHour = JdbcMssqlHistorianRollupMaterializer.floorToBucket(Instant.now(), 3_600);
            Instant openDay = JdbcMssqlHistorianRollupMaterializer.floorToBucket(Instant.now(), 86_400);
            assertThat(hourCovered).isEqualTo(openHour);
            assertThat(dayCovered).isEqualTo(openDay);

            JdbcMssqlHistorianRollupMaterializer.TickResult second = materializer.materializeTick();
            assertThat(second.ran()).isTrue();
            assertThat(materializer.loadCoveredThrough(3_600).orElseThrow()).isEqualTo(hourCovered);
        }
    }
}
