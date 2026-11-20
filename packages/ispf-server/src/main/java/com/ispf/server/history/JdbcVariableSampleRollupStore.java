package com.ispf.server.history;

import com.ispf.server.relational.JdbcUtcTimestamps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * JDBC rollup rows for MSSQL single-engine. Not the ClickHouse rollup path.
 * Uses T-SQL {@code MERGE}. Registered only via {@link JdbcVariableSampleRollupConfiguration}.
 */
public class JdbcVariableSampleRollupStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcVariableSampleRollupStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int upsertBucket(
            String objectPath,
            String variableName,
            String fieldName,
            Instant bucketStart,
            long bucketSeconds,
            double avg,
            double min,
            double max,
            long sampleCount
    ) {
        return jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement(
                    """
                            MERGE variable_sample_rollups AS target
                            USING (SELECT ? AS object_path, ? AS variable_name, ? AS field_name,
                                          ? AS bucket_start, ? AS bucket_seconds) AS source
                            ON target.object_path = source.object_path
                               AND target.variable_name = source.variable_name
                               AND target.field_name = source.field_name
                               AND target.bucket_start = source.bucket_start
                               AND target.bucket_seconds = source.bucket_seconds
                            WHEN MATCHED THEN UPDATE SET
                                avg_val = ?, min_val = ?, max_val = ?, sample_count = ?
                            WHEN NOT MATCHED THEN INSERT
                                (object_path, variable_name, field_name, bucket_start, bucket_seconds,
                                 avg_val, min_val, max_val, sample_count)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                            """);
            statement.setString(1, objectPath);
            statement.setString(2, variableName);
            statement.setString(3, fieldName);
            JdbcUtcTimestamps.bind(statement, 4, bucketStart);
            statement.setInt(5, (int) bucketSeconds);
            statement.setDouble(6, avg);
            statement.setDouble(7, min);
            statement.setDouble(8, max);
            statement.setLong(9, sampleCount);
            statement.setString(10, objectPath);
            statement.setString(11, variableName);
            statement.setString(12, fieldName);
            JdbcUtcTimestamps.bind(statement, 13, bucketStart);
            statement.setInt(14, (int) bucketSeconds);
            statement.setDouble(15, avg);
            statement.setDouble(16, min);
            statement.setDouble(17, max);
            statement.setLong(18, sampleCount);
            return statement;
        });
    }

    @Transactional(readOnly = true)
    public List<VariableHistoryService.VariableHistoryBucket> queryBuckets(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            long bucketSeconds
    ) {
        return jdbcTemplate.query(
                connection -> {
                    java.sql.PreparedStatement statement = connection.prepareStatement(
                            """
                                    SELECT bucket_start, avg_val, min_val, max_val, sample_count
                                    FROM variable_sample_rollups
                                    WHERE object_path = ?
                                      AND variable_name = ?
                                      AND field_name = ?
                                      AND bucket_seconds = ?
                                      AND bucket_start >= ?
                                      AND bucket_start <= ?
                                    ORDER BY bucket_start ASC
                                    """);
                    statement.setString(1, objectPath);
                    statement.setString(2, variableName);
                    statement.setString(3, fieldName);
                    statement.setInt(4, (int) bucketSeconds);
                    JdbcUtcTimestamps.bind(statement, 5, from);
                    JdbcUtcTimestamps.bind(statement, 6, to);
                    return statement;
                },
                (rs, rowNum) -> new VariableHistoryService.VariableHistoryBucket(
                        JdbcUtcTimestamps.read(rs, "bucket_start"),
                        rs.getDouble("avg_val"),
                        rs.getDouble("min_val"),
                        rs.getDouble("max_val"),
                        (int) Math.min(rs.getLong("sample_count"), Integer.MAX_VALUE)
                )
        );
    }
}
