package com.ispf.server.relational;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Dialect-specific SQL and catalog behavior for the ISPF relational core (ADR-0037 / ADR-0078).
 */
public interface RelationalDialect {

    RelationalDbKind kind();

    String platformSchemaPrefix();

    String defaultPlatformSchema();

    String quoteIdentifier(String identifier);

    boolean requiresSchemaDdlBeforeSwitch();

    /**
     * When {@code false}, {@link com.ispf.server.application.data.ApplicationSchemaSession}
     * must fail closed on schema switch (ADR-0078 §4 — MSSQL).
     */
    boolean supportsSessionSchemaSwitch();

    /**
     * PostgreSQL RLS / GUC path. MSSQL must boot-refuse when isolation is on until a native policy exists.
     */
    boolean supportsTenantDbRowIsolation();

    String activateSchemaSql(String quotedSchema);

    String resetPlatformSchemaSql();

    String createSchemaIfNotExistsSql(String quotedSchema);

    /**
     * SELECT listing job_id from platform_jobs for claim-next with row-level lock semantics.
     */
    String queuedPlatformJobSelectSql();

    /**
     * Native SQL for variable_samples time-bucket aggregation (parameter order is dialect-defined).
     */
    String variableSampleBucketAggregateSql();

    boolean supportsTimescaleHypertables();

    String[] flywayLocations();

    /**
     * Trailing limit clause using a single bound parameter for the row count
     * (e.g. {@code  LIMIT ?} or {@code  OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY}).
     */
    String limitClause();

    /**
     * Row lock suffix after FROM/WHERE (e.g. {@code  FOR UPDATE} or {@code  WITH (UPDLOCK, ROWLOCK)}).
     * For MSSQL the hint belongs after the table name — prefer {@link #selectTopOneForUpdate}.
     */
    String forUpdateClause();

    /**
     * {@code SELECT <columns> FROM <table> ...} taking the latest one row under a write lock.
     *
     * @param columns   column list
     * @param table     table name (optionally schema-qualified)
     * @param orderBy   ORDER BY clause without the {@code ORDER BY} keyword (e.g. {@code seq DESC})
     */
    String selectTopOneForUpdate(String columns, String table, String orderBy);

    /**
     * True for unique / primary-key conflicts only (PG {@code 23505}; MSSQL errors {@code 2627}/{@code 2601}).
     */
    boolean isUniqueViolation(SQLException ex);

    /**
     * Acquire a transaction-scoped lock for cooperative single-writer sections (audit chain, script locks).
     */
    void acquireTransactionLock(Connection connection, String lockKey, long advisoryLockId) throws SQLException;

    /**
     * Value expression for {@code now + N seconds} with one bound numeric parameter for N
     * (driver locks / leader leases — ADR-0078).
     */
    String nowPlusSecondsExpression();

    /**
     * SQL boolean literal for inline predicates ({@code TRUE}/{@code FALSE} on PG/H2; {@code 1}/{@code 0} on MSSQL).
     * Prefer bound {@code ?} parameters when practical.
     */
    String booleanLiteral(boolean value);
}
