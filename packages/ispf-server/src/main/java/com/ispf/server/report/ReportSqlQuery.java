package com.ispf.server.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * Defaults, then caller parameters. A declared name with neither a default nor a caller value
     * stays absent so binding can reject it. An empty string is not invented for a missing name.
     */
    static Map<String, Object> effectiveParameters(
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
            Object value = values.get(name);
            requireScalarParameter(name, value);
            resolved.add(value);
        }
        return resolved;
    }

    /** JSON arrays and objects are not SQL scalars. Numbers, booleans and strings still bind. */
    private static void requireScalarParameter(String name, Object value) {
        if (value instanceof Map<?, ?> || value instanceof Collection<?> || (value != null && value.getClass().isArray())) {
            throw new IllegalArgumentException("Report parameter " + name + " must be a scalar value");
        }
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
        List<Object> bound;
        if (placeholderCount == resolved.size()) {
            bound = resolved;
        } else if (placeholderCount > resolved.size() && paramNames != null && paramNames.size() == 1) {
            Object value = resolved.getFirst();
            List<Object> expanded = new ArrayList<>(placeholderCount);
            for (int i = 0; i < placeholderCount; i++) {
                expanded.add(value);
            }
            bound = expanded;
        } else {
            throw new IllegalArgumentException(
                    "Report query has " + placeholderCount + " SQL placeholder(s) but "
                            + resolved.size() + " bound parameter value(s)"
            );
        }
        validateTemporalCasts(query, paramNames, bound);
        return bound;
    }

    /**
     * A placeholder inside {@code CAST(? AS timestamp/date/time)} only accepts a temporal value
     * or a literal that parses as one. Other SQL types are left to the driver.
     */
    private static void validateTemporalCasts(String query, List<String> paramNames, List<Object> bound) {
        if (query == null || bound == null || bound.isEmpty()) {
            return;
        }
        List<Integer> positions = placeholderPositions(query);
        int limit = Math.min(bound.size(), positions.size());
        for (int i = 0; i < limit; i++) {
            if (temporalCastType(query, positions.get(i)) == null) {
                continue;
            }
            requireTemporalParameter(parameterName(paramNames, i), bound.get(i));
        }
    }

    private static String parameterName(List<String> paramNames, int placeholderIndex) {
        if (paramNames == null || paramNames.isEmpty()) {
            return "parameter";
        }
        if (paramNames.size() == 1 || placeholderIndex >= paramNames.size()) {
            return paramNames.getFirst();
        }
        return paramNames.get(placeholderIndex);
    }

    private static void requireTemporalParameter(String name, Object value) {
        if (value == null || value instanceof TemporalAccessor || value instanceof Date) {
            return;
        }
        if (value instanceof CharSequence && isTemporalLiteral(value.toString())) {
            return;
        }
        throw new IllegalArgumentException("Report parameter " + name + " must be a timestamp");
    }

    private static final DateTimeFormatter LOCAL_DATE_TIME_SPACE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static boolean isTemporalLiteral(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return false;
        }
        return parses(value, Instant::parse)
                || parses(value, OffsetDateTime::parse)
                || parses(value, LocalDateTime::parse)
                || parses(value, text -> LocalDateTime.parse(text, LOCAL_DATE_TIME_SPACE))
                || parses(value, LocalDate::parse)
                || parses(value, LocalTime::parse);
    }

    private static boolean parses(String value, java.util.function.Consumer<String> parser) {
        try {
            parser.accept(value);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static List<Integer> placeholderPositions(String query) {
        List<Integer> positions = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return positions;
        }
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
                positions.add(i);
            }
        }
        return positions;
    }

    /** {@code CAST(? AS timestamp)} and the same shape for date/time, ignoring quoted text. */
    private static String temporalCastType(String query, int questionMark) {
        int open = skipWhitespaceBackward(query, questionMark - 1);
        if (open < 0 || query.charAt(open) != '(') {
            return null;
        }
        int wordEnd = skipWhitespaceBackward(query, open - 1);
        if (wordEnd < 3 || !query.regionMatches(true, wordEnd - 3, "CAST", 0, 4)) {
            return null;
        }
        int wordStart = wordEnd - 3;
        if (wordStart > 0 && Character.isLetterOrDigit(query.charAt(wordStart - 1))) {
            return null;
        }
        int asStart = skipWhitespaceForward(query, questionMark + 1);
        if (asStart < 0 || !query.regionMatches(true, asStart, "AS", 0, 2)) {
            return null;
        }
        if (asStart + 2 < query.length() && Character.isLetterOrDigit(query.charAt(asStart + 2))) {
            return null;
        }
        int typeStart = skipWhitespaceForward(query, asStart + 2);
        if (typeStart < 0) {
            return null;
        }
        int typeEnd = typeStart;
        while (typeEnd < query.length() && query.charAt(typeEnd) != ')') {
            typeEnd++;
        }
        String type = query.substring(typeStart, typeEnd).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (type.startsWith("timestamp") || type.startsWith("timestamptz") || "date".equals(type) || type.startsWith("time")) {
            return type;
        }
        return null;
    }

    private static int skipWhitespaceBackward(String query, int index) {
        int i = index;
        while (i >= 0 && Character.isWhitespace(query.charAt(i))) {
            i--;
        }
        return i;
    }

    private static int skipWhitespaceForward(String query, int index) {
        int i = index;
        while (i < query.length() && Character.isWhitespace(query.charAt(i))) {
            i++;
        }
        return i < query.length() ? i : -1;
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
                mapped.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
            normalized.add(mapped);
        }
        return normalized;
    }
}
