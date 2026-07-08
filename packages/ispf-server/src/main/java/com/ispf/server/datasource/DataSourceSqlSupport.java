package com.ispf.server.datasource;

import java.util.List;
import java.util.regex.Pattern;

/**
 * SQL validation for ad-hoc data source queries (read and write).
 */
public final class DataSourceSqlSupport {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(GRANT|REVOKE|COPY\\s+TO|COPY\\s+FROM|\\bpg_terminate_backend\\b)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FORBIDDEN_EXTERNAL = Pattern.compile(
            "\\b(DROP|TRUNCATE|ALTER|CREATE|GRANT|REVOKE|DELETE|UPDATE|INSERT)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private DataSourceSqlSupport() {
    }

    public static String normalizeSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        if (trimmed.indexOf(';') >= 0) {
            throw new IllegalArgumentException("query must be a single SQL statement");
        }
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new IllegalArgumentException("query contains forbidden SQL keyword");
        }
        return trimmed;
    }

    public static void assertAllowedForExternal(boolean external, String sql) {
        if (!external) {
            return;
        }
        if (FORBIDDEN_EXTERNAL.matcher(sql).find()) {
            throw new IllegalArgumentException("write/DDL SQL is not allowed on external data sources");
        }
    }

    public static boolean isReadQuery(String sql) {
        String normalized = sql.stripLeading();
        return normalized.regionMatches(true, 0, "SELECT", 0, 6)
                || normalized.regionMatches(true, 0, "WITH", 0, 4)
                || normalized.regionMatches(true, 0, "SHOW", 0, 4)
                || normalized.regionMatches(true, 0, "EXPLAIN", 0, 7)
                || normalized.regionMatches(true, 0, "VALUES", 0, 6);
    }

    public static List<Object> normalizeParams(List<Object> params) {
        return params != null ? List.copyOf(params) : List.of();
    }
}
