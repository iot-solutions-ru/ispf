package com.ispf.server.relational;

public class PostgreSqlDialect extends AbstractRelationalDialect {

    @Override
    public RelationalDbKind kind() {
        return RelationalDbKind.POSTGRESQL;
    }

    @Override
    public String platformSchemaPrefix() {
        return "public.";
    }

    @Override
    public String defaultPlatformSchema() {
        return "public";
    }

    @Override
    public boolean requiresSchemaDdlBeforeSwitch() {
        // SET search_path TO <missing> succeeds, but CREATE TABLE then fails with
        // SQLSTATE 3F000 "no schema has been selected to create in".
        return true;
    }

    @Override
    public String activateSchemaSql(String quotedSchema) {
        return "SET search_path TO " + quotedSchema;
    }

    @Override
    public String resetPlatformSchemaSql() {
        return "SET search_path TO public";
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
                FOR UPDATE SKIP LOCKED
                """;
    }

    @Override
    public String variableSampleBucketAggregateSql() {
        return bucketAggregateSqlPostgresStyle();
    }

    @Override
    public boolean supportsTimescaleHypertables() {
        return true;
    }

    @Override
    public String[] flywayLocations() {
        return new String[]{"classpath:db/migration/postgresql"};
    }
}
