package com.ispf.server.relational;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * MySQL 8.4 dialect (ADR-0079 B1, opt-in single-engine B2). Boot runs only under
 * {@code MetadataDialectBootPolicy.assertMysqlB1Fences}. MariaDB is not this dialect.
 */
public class MysqlDialect extends AbstractRelationalDialect {

    @Override
    public RelationalDbKind kind() {
        return RelationalDbKind.MYSQL;
    }

    @Override
    public String platformSchemaPrefix() {
        return "";
    }

    @Override
    public String defaultPlatformSchema() {
        return "";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier is required");
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public boolean supportsSessionSchemaSwitch() {
        return false;
    }

    @Override
    public boolean requiresSchemaDdlBeforeSwitch() {
        return false;
    }

    @Override
    public String activateSchemaSql(String quotedSchema) {
        throw new UnsupportedOperationException(
                "MySQL per-application schema switching is not certified (ADR-0079). "
                        + "External DATA_SOURCE remains the supported path."
        );
    }

    @Override
    public String resetPlatformSchemaSql() {
        return "SELECT 1";
    }

    @Override
    public String createSchemaIfNotExistsSql(String quotedSchema) {
        throw new UnsupportedOperationException(
                "MySQL schema/database create for app isolation is not certified (ADR-0079)."
        );
    }

    @Override
    public String queuedPlatformJobSelectSql() {
        return """
                SELECT job_id
                FROM platform_jobs
                WHERE status = ?
                ORDER BY priority DESC, created_at
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """;
    }

    /**
     * Native MySQL 8.4 (B2). {@code TIMESTAMPDIFF}/{@code TIMESTAMPADD} work on the {@code DATETIME(6)}
     * wall clock, so buckets do not move with the session {@code time_zone} as {@code UNIX_TIMESTAMP} /
     * {@code FROM_UNIXTIME} would. Epoch-aligned like the PostgreSQL and JVM paths. Parameter order:
     * bucket seconds, bucket seconds, object path, variable, field, from, to, max buckets.
     */
    @Override
    public String variableSampleBucketAggregateSql() {
        return """
                SELECT bucket_start, avg_val, min_val, max_val, sample_count
                FROM (
                    SELECT
                        bucket_start,
                        AVG(value_double) AS avg_val,
                        MIN(value_double) AS min_val,
                        MAX(value_double) AS max_val,
                        COUNT(*) AS sample_count
                    FROM (
                        SELECT
                            TIMESTAMPADD(
                                SECOND,
                                FLOOR(TIMESTAMPDIFF(SECOND, TIMESTAMP '1970-01-01 00:00:00', observed_at) / ?) * ?,
                                TIMESTAMP '1970-01-01 00:00:00'
                            ) AS bucket_start,
                            value_double
                        FROM variable_samples
                        WHERE object_path = ?
                          AND variable_name = ?
                          AND field_name = ?
                          AND observed_at >= ?
                          AND observed_at <= ?
                          AND value_double IS NOT NULL
                    ) bucketed
                    GROUP BY bucket_start
                    ORDER BY bucket_start DESC
                    LIMIT ?
                ) recent_buckets
                ORDER BY bucket_start ASC
                """;
    }

    @Override
    public boolean supportsTimescaleHypertables() {
        return false;
    }

    @Override
    public boolean isUniqueViolation(SQLException ex) {
        if (ex == null) {
            return false;
        }
        // 1062 ER_DUP_ENTRY only — not bare SQLSTATE 23000 (FK 1452 / NOT NULL 1048).
        return ex.getErrorCode() == 1062;
    }

    /**
     * The portable {@code INSERT … WHERE NOT EXISTS} leaves racing first-time callers holding a
     * shared lock after 1062, and their {@code FOR UPDATE} upgrades deadlock (1213).
     * {@code ON DUPLICATE KEY UPDATE} takes the exclusive row lock on insert and duplicate alike.
     */
    @Override
    protected void ensureLockRow(Connection connection, String lockKey, String locksTable) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + locksTable + " (lock_key) VALUES (?) ON DUPLICATE KEY UPDATE lock_key = lock_key"
        )) {
            insert.setString(1, lockKey);
            insert.executeUpdate();
        }
    }

    @Override
    public String nowPlusSecondsExpression() {
        return com.ispf.server.spi.JdbcExpirySql.MYSQL_STYLE;
    }

    @Override
    public String booleanLiteral(boolean value) {
        return value ? "1" : "0";
    }

    @Override
    public String orderByNullsLast(String expression) {
        return expression + " IS NULL, " + expression;
    }

    /** {@code CONCAT} returns NULL when any argument is NULL, the same as PG {@code ||}. */
    @Override
    public String concatExpression(String... operands) {
        return "CONCAT(" + String.join(", ", operands) + ")";
    }

    @Override
    public String[] flywayLocations() {
        return new String[]{"classpath:db/migration/mysql"};
    }
}
