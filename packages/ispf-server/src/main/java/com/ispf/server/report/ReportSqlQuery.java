package com.ispf.server.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Read-only SQL guard rails and parameter binding for report queries. Pure functions — no
 * database access; the service decides where (schema / external data source) the query runs.
 */
final class ReportSqlQuery {

    private static final Pattern FORBIDDEN_SQL = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|CALL|EXEC|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private ReportSqlQuery() {
    }

    /** Rejects anything that is not a single SELECT / WITH statement. */
    static void validateSelectQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Report query is required");
        }
        String trimmed = query.trim();
        if (!trimmed.regionMatches(true, 0, "SELECT", 0, 6)
                && !trimmed.regionMatches(true, 0, "WITH", 0, 4)) {
            throw new IllegalArgumentException("Report query must start with SELECT or WITH");
        }
        if (FORBIDDEN_SQL.matcher(trimmed).find()) {
            throw new IllegalArgumentException("Report query contains forbidden SQL keyword");
        }
    }

    /** defaults &lt; caller parameters; every declared parameter name is present (empty string if unset). */
    static Map<String, Object> effectiveParameters(
            List<String> paramNames,
            Map<String, Object> defaultParameters,
            Map<String, Object> parameters
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (defaultParameters != null) {
            merged.putAll(defaultParameters);
        }
        if (parameters != null) {
            merged.putAll(parameters);
        }
        if (paramNames != null) {
            for (String name : paramNames) {
                merged.putIfAbsent(name, "");
            }
        }
        return merged;
    }

    private static List<Object> resolveParameterValues(
            List<String> paramNames,
            Map<String, Object> parameters
    ) {
        if (paramNames == null || paramNames.isEmpty()) {
            return List.of();
        }
        Map<String, Object> values = parameters != null ? parameters : Map.of();
        List<Object> resolved = new ArrayList<>();
        for (String name : paramNames) {
            if (!values.containsKey(name)) {
                throw new IllegalArgumentException("Missing report parameter: " + name);
            }
            resolved.add(values.get(name));
        }
        return resolved;
    }

    /**
     * Positional values for the query's {@code ?} placeholders. A single declared parameter may be
     * reused for several placeholders; any other count mismatch is an error.
     */
    static List<Object> bindQueryParameters(
            String query,
            List<String> paramNames,
            Map<String, Object> parameters
    ) {
        List<Object> resolved = resolveParameterValues(paramNames, parameters);
        int placeholderCount = countSqlPlaceholders(query);
        if (placeholderCount == resolved.size()) {
            return resolved;
        }
        if (placeholderCount > resolved.size() && paramNames != null && paramNames.size() == 1) {
            Object value = resolved.getFirst();
            List<Object> expanded = new ArrayList<>(placeholderCount);
            for (int i = 0; i < placeholderCount; i++) {
                expanded.add(value);
            }
            return expanded;
        }
        throw new IllegalArgumentException(
                "Report query has " + placeholderCount + " SQL placeholder(s) but "
                        + resolved.size() + " bound parameter value(s)"
        );
    }

    /** Counts {@code ?} outside single-quoted literals ({@code ''} escapes handled). */
    static int countSqlPlaceholders(String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        int count = 0;
        boolean inSingleQuote = false;
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            if (ch == '\'') {
                if (inSingleQuote && i + 1 < query.length() && query.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (!inSingleQuote && ch == '?') {
                count++;
            }
        }
        return count;
    }

    /** JDBC drivers differ in column-name casing; report columns are matched lower-case. */
    static List<Map<String, Object>> normalizeRowKeys(List<Map<String, Object>> rows) {
        List<Map<String, Object>> normalized = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                mapped.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            normalized.add(mapped);
        }
        return normalized;
    }
}
