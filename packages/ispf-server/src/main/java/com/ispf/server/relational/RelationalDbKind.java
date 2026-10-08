package com.ispf.server.relational;


import java.util.Locale;
/**
 * Supported metadata / relational-core database engines (ADR-0037).
 */
public enum RelationalDbKind {
    POSTGRESQL,
    H2,
    MSSQL,
    MYSQL,
    ORACLE;

    public String configValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RelationalDbKind fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "postgresql", "postgres", "pg" -> POSTGRESQL;
            case "h2" -> H2;
            case "mssql", "sqlserver", "sql-server" -> MSSQL;
            case "mysql", "mariadb" -> MYSQL;
            case "oracle" -> ORACLE;
            default -> throw new IllegalArgumentException("Unsupported ISPF_METADATA_DB_KIND: " + value);
        };
    }
}
