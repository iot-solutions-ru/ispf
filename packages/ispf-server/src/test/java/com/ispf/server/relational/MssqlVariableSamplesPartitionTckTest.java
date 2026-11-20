package com.ispf.server.relational;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ADR-0078 Gate C1: empty DB gets monthly partitions; nonempty DB records SKIPPED_NONEMPTY.
 * Skips without Docker.
 */
@Tag("mssql-docker")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class MssqlVariableSamplesPartitionTckTest {

    @Test
    void emptyDatabaseAppliesPartitions() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        try (MSSQLServerContainer mssql = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense()
                .withStartupTimeout(Duration.ofMinutes(5))) {
            mssql.start();
            JdbcTemplate jdbc = jdbcAfterMigrate(mssql, null);

            assertThat(status(jdbc)).isEqualTo("APPLIED");
            assertThat(isPartitioned(jdbc)).isTrue();
            assertThat(pkColumns(jdbc)).containsExactly("sampled_at", "id");
            assertThat(indexOnScheme(jdbc, "uq_variable_samples_series_time")).isTrue();
            assertThat(indexOnScheme(jdbc, "idx_variable_samples_lookup")).isTrue();
            assertThat(indexExists(jdbc, "idx_variable_samples_retention")).isFalse();
            assertThat(lockEscalation(jdbc)).isEqualTo("AUTO");

            Instant maxBefore = rightmostBoundary(jdbc);

            // RANGE RIGHT: boundary instant belongs to the later month (UTC wall clock in DATETIME2).
            Instant boundary = Instant.parse("2026-02-01T00:00:00Z");
            Instant before = boundary.minusSeconds(1);
            insertSample(jdbc, "root.device", "temp", boundary, 1.0);

            Integer partAtBoundary = partitionOf(jdbc, boundary);
            Integer partBefore = partitionOf(jdbc, before);
            assertThat(partAtBoundary).isGreaterThan(partBefore);

            Integer rowPartition = jdbc.queryForObject(
                    """
                            SELECT $PARTITION.pf_variable_samples_month(sampled_at)
                            FROM variable_samples
                            WHERE object_path = ? AND variable_name = ? AND sampled_at = CAST(? AS DATETIME2)
                            """,
                    Integer.class,
                    "root.device", "temp", "2026-02-01T00:00:00"
            );
            assertThat(rowPartition).isEqualTo(partAtBoundary);

            assertThatThrownBy(() -> insertSample(jdbc, "root.device", "temp", boundary, 2.0))
                    .isInstanceOf(DuplicateKeyException.class);

            // Horizon extend: sample is before 2030, so rightmost partition stays empty.
            runSplitScript(jdbc, 1);
            Instant maxAfter = rightmostBoundary(jdbc);
            assertThat(maxAfter).isAfter(maxBefore);

            // Refuse SPLIT when rightmost partition has rows.
            insertSample(jdbc, "root.device", "far", maxAfter, 3.0);
            assertThatThrownBy(() -> runSplitScript(jdbc, 1))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("Rightmost partition is not empty");
        }
    }

    @Test
    void nonemptyDatabaseSkipsPartitionRewrite() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        try (MSSQLServerContainer mssql = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense()
                .withStartupTimeout(Duration.ofMinutes(5))) {
            mssql.start();
            String jdbcUrl = MssqlTestSupport.prepareIspfDatabase(mssql);
            DriverManagerDataSource dataSource = dataSource(mssql, jdbcUrl);
            MssqlDialect dialect = new MssqlDialect();

            Flyway.configure()
                    .dataSource(dataSource)
                    .locations(dialect.flywayLocations())
                    .target("101.1")
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertSample(jdbc, "root.device", "temp", Instant.parse("2026-01-15T12:00:00Z"), 9.0);

            Flyway.configure()
                    .dataSource(dataSource)
                    .locations(dialect.flywayLocations())
                    .load()
                    .migrate();

            assertThat(status(jdbc)).isEqualTo("SKIPPED_NONEMPTY");
            assertThat(isPartitioned(jdbc)).isFalse();
            assertThat(pkColumns(jdbc)).containsExactly("id");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM variable_samples", Integer.class)).isEqualTo(1);

            assertThatThrownBy(() -> runSplitScript(jdbc, 1))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("not APPLIED");
        }
    }

    private static JdbcTemplate jdbcAfterMigrate(MSSQLServerContainer mssql, String target) {
        String jdbcUrl = MssqlTestSupport.prepareIspfDatabase(mssql);
        DriverManagerDataSource dataSource = dataSource(mssql, jdbcUrl);
        var config = Flyway.configure()
                .dataSource(dataSource)
                .locations(new MssqlDialect().flywayLocations());
        if (target != null) {
            config.target(target);
        }
        config.load().migrate();
        return new JdbcTemplate(dataSource);
    }

    private static DriverManagerDataSource dataSource(MSSQLServerContainer mssql, String jdbcUrl) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(mssql.getDriverClassName());
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(mssql.getUsername());
        dataSource.setPassword(mssql.getPassword());
        return dataSource;
    }

    private static String status(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT status FROM mssql_historian_partition_status WHERE table_name = ?",
                String.class,
                "variable_samples"
        );
    }

    private static boolean isPartitioned(JdbcTemplate jdbc) {
        Integer n = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM sys.tables t
                        INNER JOIN sys.indexes i
                            ON i.object_id = t.object_id AND i.is_primary_key = 1
                        INNER JOIN sys.partition_schemes ps
                            ON i.data_space_id = ps.data_space_id
                        WHERE t.name = 'variable_samples'
                        """,
                Integer.class
        );
        return n != null && n > 0;
    }

    private static List<String> pkColumns(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                """
                        SELECT c.name
                        FROM sys.indexes i
                        INNER JOIN sys.index_columns ic
                            ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                        INNER JOIN sys.columns c
                            ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                        WHERE i.object_id = OBJECT_ID('variable_samples')
                          AND i.is_primary_key = 1
                        ORDER BY ic.key_ordinal
                        """,
                String.class
        );
    }

    private static boolean indexOnScheme(JdbcTemplate jdbc, String indexName) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT i.name AS index_name, ps.name AS scheme_name
                        FROM sys.indexes i
                        INNER JOIN sys.partition_schemes ps ON i.data_space_id = ps.data_space_id
                        WHERE i.object_id = OBJECT_ID('variable_samples')
                          AND i.name = ?
                          AND ps.name = 'ps_variable_samples_month'
                        """,
                indexName
        );
        return rows.size() == 1;
    }

    private static boolean indexExists(JdbcTemplate jdbc, String indexName) {
        Integer n = jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM sys.indexes
                        WHERE object_id = OBJECT_ID('variable_samples') AND name = ?
                        """,
                Integer.class,
                indexName
        );
        return n != null && n > 0;
    }

    private static String lockEscalation(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT lock_escalation_desc FROM sys.tables WHERE name = 'variable_samples'",
                String.class
        );
    }

    private static Instant rightmostBoundary(JdbcTemplate jdbc) {
        return jdbc.query(
                connection -> connection.prepareStatement(
                        """
                                SELECT MAX(CAST(prv.value AS DATETIME2)) AS max_boundary
                                FROM sys.partition_functions pf
                                INNER JOIN sys.partition_range_values prv ON prv.function_id = pf.function_id
                                WHERE pf.name = 'pf_variable_samples_month'
                                """
                ),
                rs -> {
                    rs.next();
                    return JdbcUtcTimestamps.read(rs, "max_boundary");
                }
        );
    }

    private static void runSplitScript(JdbcTemplate jdbc, int monthsToAdd) throws Exception {
        Path script = resolveSplitScript();
        String sql = Files.readString(script)
                .replace("$(MonthsToAdd)", Integer.toString(monthsToAdd));
        for (String batch : Arrays.stream(sql.split("(?m)^\\s*GO\\s*$"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList())) {
            jdbc.execute(batch);
        }
    }

    private static Path resolveSplitScript() {
        Path[] candidates = {
                Path.of("tools/mssql-b1-lab/split-variable-samples-horizon.sql"),
                Path.of("../tools/mssql-b1-lab/split-variable-samples-horizon.sql"),
                Path.of("../../tools/mssql-b1-lab/split-variable-samples-horizon.sql"),
                Path.of("../../../tools/mssql-b1-lab/split-variable-samples-horizon.sql")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("split-variable-samples-horizon.sql not found from " + Path.of("").toAbsolutePath());
    }

    private static void insertSample(
            JdbcTemplate jdbc, String path, String name, Instant sampledAt, double value
    ) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                            INSERT INTO variable_samples
                              (object_path, variable_name, field_name, sampled_at, observed_at, value_double)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """
            );
            statement.setString(1, path);
            statement.setString(2, name);
            statement.setString(3, "value");
            JdbcUtcTimestamps.bind(statement, 4, sampledAt);
            JdbcUtcTimestamps.bind(statement, 5, sampledAt);
            statement.setDouble(6, value);
            return statement;
        });
    }

    private static Integer partitionOf(JdbcTemplate jdbc, Instant instant) {
        return jdbc.query(
                connection -> {
                    var statement = connection.prepareStatement(
                            "SELECT $PARTITION.pf_variable_samples_month(?)"
                    );
                    JdbcUtcTimestamps.bind(statement, 1, instant);
                    return statement;
                },
                rs -> {
                    rs.next();
                    return rs.getInt(1);
                }
        );
    }
}
