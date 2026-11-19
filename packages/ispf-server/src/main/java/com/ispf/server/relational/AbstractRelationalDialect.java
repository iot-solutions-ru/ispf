package com.ispf.server.relational;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Locale;

abstract class AbstractRelationalDialect implements RelationalDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier is required");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public boolean supportsSessionSchemaSwitch() {
        return true;
    }

    @Override
    public boolean supportsTenantDbRowIsolation() {
        return false;
    }

    @Override
    public String limitClause() {
        return " LIMIT ?";
    }

    @Override
    public String forUpdateClause() {
        return " FOR UPDATE";
    }

    @Override
    public String selectTopOneForUpdate(String columns, String table, String orderBy) {
        return "SELECT " + columns + " FROM " + table
                + " ORDER BY " + orderBy
                + limitOneLiteral()
                + forUpdateClause();
    }

    /** Literal single-row limit for lock-head queries (no bind parameter). */
    protected String limitOneLiteral() {
        return " LIMIT 1";
    }

    @Override
    public boolean isUniqueViolation(SQLException ex) {
        if (ex == null) {
            return false;
        }
        if ("23505".equals(ex.getSQLState())) {
            return true;
        }
        // H2 and others may surface integrity violations without PG SQLSTATE.
        return ex instanceof SQLIntegrityConstraintViolationException
                && messageLooksLikeUnique(ex.getMessage());
    }

    @Override
    public void acquireTransactionLock(Connection connection, String lockKey, long advisoryLockId)
            throws SQLException {
        String locksTable = platformScriptLocksTable(connection);
        ensureLockRow(connection, lockKey, locksTable);
        try (PreparedStatement rowLock = connection.prepareStatement(
                "SELECT lock_key FROM " + locksTable + " WHERE lock_key = ?" + forUpdateClause()
        )) {
            rowLock.setString(1, lockKey);
            rowLock.executeQuery().close();
        }
    }

    protected String platformScriptLocksTable(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (product.contains("h2")) {
            return "PUBLIC.platform_script_locks";
        }
        return platformSchemaPrefix() + "platform_script_locks";
    }

    protected void ensureLockRow(Connection connection, String lockKey, String locksTable) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO %s (lock_key)
                SELECT ? WHERE NOT EXISTS (SELECT 1 FROM %s WHERE lock_key = ?)
                """.formatted(locksTable, locksTable))) {
            insert.setString(1, lockKey);
            insert.setString(2, lockKey);
            insert.executeUpdate();
        } catch (SQLException ex) {
            if (!isUniqueViolation(ex)) {
                throw ex;
            }
        }
    }

    private static boolean messageLooksLikeUnique(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("unique") || lower.contains("duplicate") || lower.contains("primary key");
    }

    @Override
    public String nowPlusSecondsExpression() {
        return com.ispf.server.spi.JdbcExpirySql.POSTGRES_STYLE;
    }

    protected static String bucketAggregateSqlPostgresStyle() {
        return """
                SELECT *
                FROM (
                    SELECT
                        to_timestamp(floor(extract(epoch from COALESCE(observed_at, sampled_at)) / ?) * ?)
                            AS bucket_start,
                        AVG(value_double) AS avg_val,
                        MIN(value_double) AS min_val,
                        MAX(value_double) AS max_val,
                        COUNT(*) AS sample_count
                    FROM variable_samples
                    WHERE object_path = ?
                      AND variable_name = ?
                      AND field_name = ?
                      AND COALESCE(observed_at, sampled_at) >= ?
                      AND COALESCE(observed_at, sampled_at) <= ?
                      AND value_double IS NOT NULL
                    GROUP BY 1
                    ORDER BY 1 DESC
                    LIMIT ?
                ) recent_buckets
                ORDER BY bucket_start ASC
                """;
    }

    @Override
    public String booleanLiteral(boolean value) {
        return value ? "TRUE" : "FALSE";
    }
}
