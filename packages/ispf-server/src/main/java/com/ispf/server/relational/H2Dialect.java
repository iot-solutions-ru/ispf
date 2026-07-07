package com.ispf.server.relational;

public class H2Dialect extends AbstractRelationalDialect {

    @Override
    public RelationalDbKind kind() {
        return RelationalDbKind.H2;
    }

    @Override
    public String platformSchemaPrefix() {
        return "PUBLIC.";
    }

    @Override
    public String defaultPlatformSchema() {
        return "PUBLIC";
    }

    @Override
    public boolean requiresSchemaDdlBeforeSwitch() {
        return true;
    }

    @Override
    public String activateSchemaSql(String quotedSchema) {
        return "SET SCHEMA " + quotedSchema;
    }

    @Override
    public String resetPlatformSchemaSql() {
        return "SET SCHEMA PUBLIC";
    }

    @Override
    public String createSchemaIfNotExistsSql(String quotedSchema) {
        return "CREATE SCHEMA IF NOT EXISTS " + quotedSchema;
    }

    @Override
    public String queuedPlatformJobSelectSql() {
        return """
                SELECT job_id
                FROM platform_jobs
                WHERE status = ?
                ORDER BY priority DESC, created_at
                LIMIT 1
                FOR UPDATE
                """;
    }

    @Override
    public String variableSampleBucketAggregateSql() {
        return bucketAggregateSqlPostgresStyle();
    }

    @Override
    public boolean supportsTimescaleHypertables() {
        return false;
    }

    @Override
    public String[] flywayLocations() {
        return new String[]{"classpath:db/migration/h2", "classpath:db/migration/postgresql"};
    }
}
