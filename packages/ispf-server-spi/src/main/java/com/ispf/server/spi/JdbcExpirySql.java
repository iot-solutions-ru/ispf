package com.ispf.server.spi;

import java.util.Locale;

/**
 * Portable {@code now + N seconds} value expressions for JDBC locks (ADR-0078).
 * Keeps {@code ispf-server-driver} free of a dependency on {@code ispf-server} dialects.
 */
public final class JdbcExpirySql {

    private JdbcExpirySql() {
    }

    /** PostgreSQL / H2 (PostgreSQL mode). */
    public static final String POSTGRES_STYLE =
            "CURRENT_TIMESTAMP + (CAST(? AS BIGINT) * INTERVAL '1' SECOND)";

    /**
     * Microsoft SQL Server — use {@code CURRENT_TIMESTAMP} (same clock as
     * {@code DriverOwnershipService} comparisons), not {@code SYSUTCDATETIME()}, so locks
     * stay coherent on non-UTC hosts.
     */
    public static final String MSSQL_STYLE = "DATEADD(SECOND, ?, CURRENT_TIMESTAMP)";

    public static String nowPlusSecondsExpression(String databaseProductName) {
        if (databaseProductName == null || databaseProductName.isBlank()) {
            return POSTGRES_STYLE;
        }
        String lower = databaseProductName.toLowerCase(Locale.ROOT);
        if (lower.contains("microsoft") || lower.contains("sql server")) {
            return MSSQL_STYLE;
        }
        return POSTGRES_STYLE;
    }
}
