package com.ispf.server.application.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ApplicationReportService {

    private static final Pattern FORBIDDEN_SQL = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|CALL|EXEC|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final ApplicationReportStore reportStore;
    private final ApplicationDataStore dataStore;
    private final ApplicationSchemaSession schemaSession;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ApplicationReportService(
            ApplicationReportStore reportStore,
            ApplicationDataStore dataStore,
            ApplicationSchemaSession schemaSession,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.reportStore = reportStore;
        this.dataStore = dataStore;
        this.schemaSession = schemaSession;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void deploy(String appId, DeployReportRequest request) {
        requireApp(appId);
        validateSelectQuery(request.query());
        String parametersJson = serialize(request.parameters());
        String columnsJson = serialize(request.columns());
        int maxRows = request.maxRows() != null && request.maxRows() > 0 ? request.maxRows() : 1000;

        reportStore.upsert(new ApplicationReportStore.DeployedReport(
                UUID.randomUUID(),
                appId,
                request.reportId(),
                request.title(),
                request.description(),
                request.query().trim(),
                parametersJson,
                columnsJson,
                maxRows
        ));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String appId) {
        requireApp(appId);
        return reportStore.listByApp(appId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> run(String appId, String reportId, Map<String, Object> parameters) {
        ApplicationReportStore.DeployedReport report = reportStore.find(appId, reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        List<String> paramNames = deserializeStringList(report.parametersJson());
        List<Object> paramValues = resolveParameterValues(paramNames, parameters);
        String schemaName = schemaName(appId);

        List<Map<String, Object>>[] result = new List[1];
        schemaSession.runInSchema(schemaName, () ->
                result[0] = jdbcTemplate.queryForList(report.querySql(), paramValues.toArray())
        );

        List<Map<String, Object>> rows = result[0];
        boolean truncated = rows.size() > report.maxRows();
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, report.maxRows()));
        }

        List<Map<String, String>> columns = deserializeColumns(report.columnsJson());
        rows = normalizeRowKeys(rows);
        return Map.of(
                "reportId", report.reportId(),
                "title", report.title(),
                "columns", columns,
                "rows", rows,
                "rowCount", rows.size(),
                "truncated", truncated
        );
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String appId, String reportId, Map<String, Object> parameters) {
        Map<String, Object> result = run(appId, reportId, parameters);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> columns = (List<Map<String, String>>) result.get("columns");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");

        StringBuilder csv = new StringBuilder();
        List<String> fields = columns.stream().map(col -> col.get("field")).toList();
        csv.append(columns.stream().map(col -> escapeCsv(col.get("label"))).reduce((a, b) -> a + "," + b).orElse(""));
        csv.append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    csv.append(',');
                }
                Object value = row.get(fields.get(i));
                csv.append(escapeCsv(value == null ? "" : value.toString()));
            }
            csv.append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, Object> toSummary(ApplicationReportStore.DeployedReport report) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reportId", report.reportId());
        summary.put("title", report.title());
        summary.put("description", report.description());
        summary.put("parameters", deserializeStringList(report.parametersJson()));
        summary.put("columns", deserializeColumns(report.columnsJson()));
        summary.put("maxRows", report.maxRows());
        return summary;
    }

    private void requireApp(String appId) {
        dataStore.findApp(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not registered: " + appId));
    }

    private String schemaName(String appId) {
        return dataStore.findApp(appId)
                .map(row -> (String) row.get("schema_name"))
                .orElseThrow();
    }

    private static void validateSelectQuery(String query) {
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

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize report metadata", ex);
        }
    }

    private List<String> deserializeStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid report parameters JSON", ex);
        }
    }

    private List<Map<String, String>> deserializeColumns(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid report columns JSON", ex);
        }
    }

    private static List<Map<String, Object>> normalizeRowKeys(List<Map<String, Object>> rows) {
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

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public record ReportColumn(String field, String label) {
    }

    public record DeployReportRequest(
            String reportId,
            String title,
            String description,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Integer maxRows
    ) {
    }
}
