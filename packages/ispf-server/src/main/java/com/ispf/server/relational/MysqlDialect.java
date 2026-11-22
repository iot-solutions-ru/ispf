package com.ispf.server.relational;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * MySQL 8.4 dialect (ADR-0079 B1 lab). Boot runs only under
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
     * Still the PostgreSQL-shaped string — invalid on MySQL. B2 must replace this;
     * do not treat it as a historian claim.
     */
    @Override
    public String variableSampleBucketAggregateSql() {
        return bucketAggregateSqlPostgresStyle();
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
    public String[] flywayLocations() {
        return new String[]{"classpath:db/migration/mysql"};
    }
}
