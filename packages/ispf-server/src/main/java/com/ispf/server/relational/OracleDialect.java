package com.ispf.server.relational;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Oracle dialect (ADR-0080 B1 lab). Boot needs the Flyway pack and an operator-supplied JDBC driver
 * ({@code MetadataDialectBootPolicy.assertEngineCertified}) and runs only under
 * {@code assertOracleB1Fences}. SQL stays 19c-compatible: 23ai accepts {@code SELECT} without
 * {@code FROM} and a row limit with {@code FOR UPDATE}, 19c rejects both (ORA-00923 / ORA-02014).
 */
public class OracleDialect extends AbstractRelationalDialect {

    @Override
    public RelationalDbKind kind() {
        return RelationalDbKind.ORACLE;
    }

    @Override
    public String platformSchemaPrefix() {
        return "";
    }

    @Override
    public String defaultPlatformSchema() {
        return "";
    }

    /**
     * Folds to upper case so quoted references match Oracle's dictionary form of unquoted DDL
     * ({@code platform_jobs} → {@code "PLATFORM_JOBS"}). Oracle quoted identifiers cannot contain a
     * double quote or NUL at all (there is no escape), so those fail closed.
     */
    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier is required");
        }
        if (identifier.indexOf('"') >= 0 || identifier.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(
                    "Oracle identifiers cannot contain a double quote or NUL (ADR-0080): " + identifier
            );
        }
        return "\"" + identifier.toUpperCase(Locale.ROOT) + "\"";
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
                "Oracle per-application schema switching (ALTER SESSION SET CURRENT_SCHEMA) "
                        + "is not certified (ADR-0080). External DATA_SOURCE remains the supported path."
        );
    }

    @Override
    public String resetPlatformSchemaSql() {
        return "SELECT 1 FROM DUAL";
    }

    @Override
    public String createSchemaIfNotExistsSql(String quotedSchema) {
        throw new UnsupportedOperationException(
                "Oracle schema creation for app isolation is not certified (ADR-0080). "
                        + "An Oracle schema is a database user, not CREATE SCHEMA."
        );
    }

    @Override
    public String limitClause() {
        return " FETCH FIRST ? ROWS ONLY";
    }

    /**
     * Picks the head ROWID first and locks only that row: 19c refuses a row limit with
     * {@code FOR UPDATE} (ORA-02014), and {@code FOR UPDATE} without one locks every row.
     * Appenders serialize on {@link #acquireTransactionLock} before reading the head.
     */
    @Override
    public String selectTopOneForUpdate(String columns, String table, String orderBy) {
        return "SELECT " + columns + " FROM " + table
                + " WHERE ROWID = (SELECT head_rowid FROM (SELECT ROWID AS head_rowid FROM " + table
                + " ORDER BY " + orderBy + ") WHERE ROWNUM = 1)"
                + forUpdateClause();
    }

    /**
     * No row limit: 19c refuses one with {@code FOR UPDATE} (ORA-02014), and a limit applied before
     * {@code SKIP LOCKED} finds nothing while the head job is locked. {@code SKIP LOCKED} locks rows
     * as they are fetched, so the caller must fetch a single row.
     */
    @Override
    public String queuedPlatformJobSelectSql() {
        return """
                SELECT job_id
                FROM platform_jobs
                WHERE status = ?
                ORDER BY priority DESC, created_at
                FOR UPDATE SKIP LOCKED
                """;
    }

    /**
     * Still the PostgreSQL-shaped string — invalid on Oracle. B2 must replace this;
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
        // ORA-00001 only — not bare SQLSTATE 23000 (FK ORA-02291/02292, NOT NULL ORA-01400, CHECK ORA-02290).
        return ex.getErrorCode() == 1;
    }

    /**
     * {@code FROM DUAL}: 19c has no {@code SELECT} without {@code FROM}. A racing first insert waits on
     * the other transaction's row and then fails with ORA-00001, which is ignored.
     */
    @Override
    protected void ensureLockRow(Connection connection, String lockKey, String locksTable) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO %s (lock_key)
                SELECT ? FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM %s WHERE lock_key = ?)
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

    @Override
    public String nowPlusSecondsExpression() {
        return com.ispf.server.spi.JdbcExpirySql.ORACLE_STYLE;
    }

    @Override
    public String booleanLiteral(boolean value) {
        return value ? "1" : "0";
    }

    @Override
    public String[] flywayLocations() {
        return new String[]{"classpath:db/migration/oracle"};
    }
}
