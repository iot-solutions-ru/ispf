package com.ispf.server.relational;

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
    public boolean requiresSchemaDdlBeforeSwitch() {
        return true;
    }

    @Override
    public String activateSchemaSql(String quotedSchema) {
        return "USE " + quotedSchema;
    }

    @Override
    public String resetPlatformSchemaSql() {
        return "USE ispf";
    }

    @Override
    public String createSchemaIfNotExistsSql(String quotedSchema) {
        return "CREATE DATABASE IF NOT EXISTS " + quotedSchema;
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
        return false;
    }

    @Override
    public String[] flywayLocations() {
        return new String[]{"classpath:db/migration/mysql"};
    }
}
