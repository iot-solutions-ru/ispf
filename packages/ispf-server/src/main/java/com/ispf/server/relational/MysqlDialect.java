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
                "MySQL per-application schema switching is not certified (ADR-0037). "
                        + "External DATA_SOURCE remains the supported path."
        );
    }

    @Override
    public String resetPlatformSchemaSql() {
        return null;
    }

    @Override
    public String createSchemaIfNotExistsSql(String quotedSchema) {
        throw new UnsupportedOperationException(
                "MySQL schema/database create for app isolation is not certified (ADR-0037)."
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
