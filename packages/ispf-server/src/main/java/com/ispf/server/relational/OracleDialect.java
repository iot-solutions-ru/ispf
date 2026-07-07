package com.ispf.server.relational;

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

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier is required");
        }
        return "\"" + identifier.toUpperCase().replace("\"", "\"\"") + "\"";
    }

    @Override
    public boolean requiresSchemaDdlBeforeSwitch() {
        return true;
    }

    @Override
    public String activateSchemaSql(String quotedSchema) {
        return "ALTER SESSION SET CURRENT_SCHEMA = " + quotedSchema;
    }

    @Override
    public String resetPlatformSchemaSql() {
        return "ALTER SESSION SET CURRENT_SCHEMA = ISPF";
    }

    @Override
    public String createSchemaIfNotExistsSql(String quotedSchema) {
        throw new UnsupportedOperationException("Oracle schema creation is not automated yet");
    }

    @Override
    public String queuedPlatformJobSelectSql() {
        return """
                SELECT job_id
                FROM platform_jobs
                WHERE status = ?
                ORDER BY priority DESC, created_at
                FETCH FIRST 1 ROW ONLY
                FOR UPDATE SKIP LOCKED
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
        return new String[]{"classpath:db/migration/oracle"};
    }
}
