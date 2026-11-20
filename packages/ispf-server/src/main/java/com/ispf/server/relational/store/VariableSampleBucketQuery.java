package com.ispf.server.relational.store;

import com.ispf.server.persistence.VariableSampleBucketAggregate;
import com.ispf.server.relational.JdbcUtcTimestamps;
import com.ispf.server.relational.RelationalDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Dialect-aware JDBC queries for variable_samples aggregations (ADR-0037).
 */
@Component
public class VariableSampleBucketQuery {

    private final JdbcTemplate jdbcTemplate;
    private final RelationalDialect dialect;

    public VariableSampleBucketQuery(JdbcTemplate jdbcTemplate, RelationalDialect dialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
    }

    public List<VariableSampleBucketAggregate> aggregateBuckets(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            long bucketSeconds,
            int maxBuckets
    ) {
        if (dialect.kind() == com.ispf.server.relational.RelationalDbKind.MSSQL) {
            return jdbcTemplate.query(
                    dialect.variableSampleBucketAggregateSql(),
                    (org.springframework.jdbc.core.PreparedStatementSetter) ps -> {
                        ps.setInt(1, maxBuckets);
                        ps.setLong(2, bucketSeconds);
                        ps.setString(3, objectPath);
                        ps.setString(4, variableName);
                        ps.setString(5, fieldName);
                        JdbcUtcTimestamps.bind(ps, 6, from);
                        JdbcUtcTimestamps.bind(ps, 7, to);
                        ps.setLong(8, bucketSeconds);
                    },
                    (rs, rowNum) -> new SimpleVariableSampleBucketAggregate(
                            rs.getTimestamp("bucket_start").toInstant(),
                            rs.getDouble("avg_val"),
                            rs.getDouble("min_val"),
                            rs.getDouble("max_val"),
                            rs.getLong("sample_count")
                    )
            );
        }
        return jdbcTemplate.query(
                dialect.variableSampleBucketAggregateSql(),
                (rs, rowNum) -> new SimpleVariableSampleBucketAggregate(
                        rs.getTimestamp("bucket_start").toInstant(),
                        rs.getDouble("avg_val"),
                        rs.getDouble("min_val"),
                        rs.getDouble("max_val"),
                        rs.getLong("sample_count")
                ),
                bucketSeconds,
                bucketSeconds,
                objectPath,
                variableName,
                fieldName,
                Timestamp.from(from),
                Timestamp.from(to),
                maxBuckets
        );
    }
}
