package com.ispf.server.relational;

/**
 * MS SQL Server dialect (enterprise POC, ADR-0037). Migration pack under {@code db/migration/mssql/} is populated incrementally.
 */
public class MssqlDialect extends AbstractRelationalDialect {

    @Override
    public RelationalDbKind kind() {
        return RelationalDbKind.MSSQL;
    }

    @Override
    public String platformSchemaPrefix() {
        return "dbo.";
    }

    @Override
    public String defaultPlatformSchema() {
        return "dbo";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier is required");
        }
        return "[" + identifier.replace("]", "]]") + "]";
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
        return "USE dbo";
    }

    @Override
    public String createSchemaIfNotExistsSql(String quotedSchema) {
        return "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = "
                + stripBrackets(quotedSchema) + ") EXEC('CREATE SCHEMA " + quotedSchema + "')";
    }

    private static String stripBrackets(String quoted) {
        String inner = quoted;
        if (inner.startsWith("[") && inner.endsWith("]")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        return "'" + inner.replace("'", "''") + "'";
    }

    @Override
    public String queuedPlatformJobSelectSql() {
        return """
                SELECT TOP (1) job_id
                FROM platform_jobs WITH (UPDLOCK, ROWLOCK, READPAST)
                WHERE status = ?
                ORDER BY priority DESC, created_at
                """;
    }

    @Override
    public String variableSampleBucketAggregateSql() {
        return """
                SELECT *
                FROM (
                    SELECT TOP (?)
                        DATEADD(SECOND, (DATEDIFF(SECOND, '1970-01-01', COALESCE(observed_at, sampled_at)) / ?) * ?, '1970-01-01')
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
                    GROUP BY DATEADD(SECOND, (DATEDIFF(SECOND, '1970-01-01', COALESCE(observed_at, sampled_at)) / ?) * ?, '1970-01-01')
                    ORDER BY bucket_start DESC
                ) recent_buckets
                ORDER BY bucket_start ASC
                """;
    }

    @Override
    public boolean supportsTimescaleHypertables() {
        return false;
    }

    @Override
    public String[] flywayLocations() {
        return new String[]{"classpath:db/migration/mssql"};
    }
}
