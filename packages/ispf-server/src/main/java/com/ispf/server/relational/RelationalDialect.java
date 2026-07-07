package com.ispf.server.relational;

/**
 * Dialect-specific SQL and catalog behavior for the ISPF relational core (ADR-0037).
 */
public interface RelationalDialect {

    RelationalDbKind kind();

    String platformSchemaPrefix();

    String defaultPlatformSchema();

    String quoteIdentifier(String identifier);

    boolean requiresSchemaDdlBeforeSwitch();

    String activateSchemaSql(String quotedSchema);

    String resetPlatformSchemaSql();

    String createSchemaIfNotExistsSql(String quotedSchema);

    /**
     * SELECT listing job_id from platform_jobs for claim-next with row-level lock semantics.
     */
    String queuedPlatformJobSelectSql();

    /**
     * Native SQL for variable_samples time-bucket aggregation (PostgreSQL-style parameters).
     */
    String variableSampleBucketAggregateSql();

    boolean supportsTimescaleHypertables();

    String[] flywayLocations();
}
