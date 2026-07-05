package com.ispf.server.application.data;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApplicationSchemaSupport {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\"`]?)([a-zA-Z_][a-zA-Z0-9_]*)([\"`]?)"
    );

    private static final Set<String> RESERVED_TABLES = Set.of(
            "applications",
            "application_data_migrations",
            "application_data_seeds",
            "application_functions",
            "platform_schedules",
            "blueprint_definitions",
            "function_invoke_audit",
            "binding_invoke_audit",
            "workflow_cancel_journal",
            "flyway_schema_history",
            "objects",
            "object_variables",
            "object_events",
            "workflow_instances",
            "workflow_tasks",
            "dashboards",
            "alert_rules",
            "event_correlators"
    );

    private static final Pattern FORBIDDEN_SELECT_SQL = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|CALL|EXEC|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private ApplicationSchemaSupport() {
    }

    public static String defaultSchemaName(String appId) {
        String sanitized = appId.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("appId must contain alphanumeric characters");
        }
        return "app_" + sanitized;
    }

    public static String quoteIdentifier(String identifier) {
        if (!identifier.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return identifier;
    }

    public static void validateMigrationSql(String sql, String tablePrefix) {
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(2).toLowerCase();
            if (RESERVED_TABLES.contains(tableName)) {
                throw new IllegalArgumentException(
                        "Migration creates reserved platform table: " + tableName
                );
            }
            if (tablePrefix != null && !tablePrefix.isBlank()
                    && !tableName.startsWith(tablePrefix.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Table " + tableName + " must start with tablePrefix: " + tablePrefix
                );
            }
        }
    }

    public static void validateSelectQuery(String sql, String label) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        String trimmed = sql.trim();
        if (!trimmed.regionMatches(true, 0, "SELECT", 0, 6)
                && !trimmed.regionMatches(true, 0, "WITH", 0, 4)) {
            throw new IllegalArgumentException(label + " must start with SELECT or WITH");
        }
        if (trimmed.indexOf(';') >= 0) {
            throw new IllegalArgumentException(label + " must not contain statement separators");
        }
        if (FORBIDDEN_SELECT_SQL.matcher(trimmed).find()) {
            throw new IllegalArgumentException(label + " contains forbidden SQL keyword");
        }
    }
}
