package com.ispf.server.operator;

import com.ispf.server.ai.agent.OperatorAgentScope;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Enriches operator agent finish payloads with navigation URLs and tabular previews from tool steps.
 */
@Component
public class OperatorAgentResultEnricher {

    public Map<String, Object> enrich(
            String appId,
            OperatorAgentScope scope,
            List<Map<String, Object>> steps,
            Map<String, Object> finishResult
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (finishResult != null) {
            merged.putAll(finishResult);
        }
        mergeLinks(appId, scope, merged);
        mergeTablesFromSteps(steps, merged);
        inferLinksFromTables(appId, scope, merged);
        return merged.isEmpty() ? Map.of() : merged;
    }

    public static String operatorUrl(String appId, String kind, String path) {
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        String param = "report".equals(kind) ? "report" : "dashboard";
        return "/?mode=operator&app=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                + "&" + param + "=" + encodedPath;
    }

    @SuppressWarnings("unchecked")
    private void mergeLinks(String appId, OperatorAgentScope scope, Map<String, Object> merged) {
        Object raw = merged.get("links");
        List<Map<String, Object>> links = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> link = normalizeLink(appId, scope, (Map<String, Object>) map);
                if (link != null && seen.add(linkKey(link))) {
                    links.add(link);
                }
            }
        }
        merged.put("links", links);
    }

    @SuppressWarnings("unchecked")
    private void mergeTablesFromSteps(List<Map<String, Object>> steps, Map<String, Object> merged) {
        if (merged.containsKey("tables") || merged.containsKey("table")) {
            return;
        }
        List<Map<String, Object>> tables = new ArrayList<>();
        if (steps == null) {
            return;
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            Map<String, Object> step = steps.get(i);
            if (!"tool".equals(step.get("type")) || !"run_report".equals(step.get("tool"))) {
                continue;
            }
            Object toolResult = step.get("result");
            if (!(toolResult instanceof Map<?, ?> resultMap)) {
                continue;
            }
            if (!"OK".equals(String.valueOf(resultMap.get("status")))) {
                continue;
            }
            String reportPath = stringValue(resultMap.get("path"));
            Object inner = resultMap.get("result");
            if (!(inner instanceof Map<?, ?> reportData)) {
                continue;
            }
            Map<String, Object> table = tableFromReport(reportPath, (Map<String, Object>) reportData);
            if (!table.isEmpty()) {
                tables.add(table);
                break;
            }
        }
        if (!tables.isEmpty()) {
            merged.put("tables", tables);
        }
    }

    @SuppressWarnings("unchecked")
    private void inferLinksFromTables(String appId, OperatorAgentScope scope, Map<String, Object> merged) {
        Object tablesRaw = merged.get("tables");
        if (!(tablesRaw instanceof List<?> tables) || tables.isEmpty()) {
            return;
        }
        List<Map<String, Object>> links = linksList(merged);
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> link : links) {
            seen.add(linkKey(link));
        }
        for (Object item : tables) {
            if (!(item instanceof Map<?, ?> table)) {
                continue;
            }
            String reportPath = stringValue(table.get("reportPath"));
            if (reportPath.isBlank() || !isPathAllowed(reportPath, scope)) {
                continue;
            }
            Map<String, Object> link = Map.of(
                    "kind", "report",
                    "path", reportPath,
                    "title", stringValue(table.get("title")).isBlank() ? reportPath : stringValue(table.get("title")),
                    "url", operatorUrl(appId, "report", reportPath)
            );
            if (seen.add(linkKey(link))) {
                links.add(link);
            }
        }
        merged.put("links", links);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> linksList(Map<String, Object> merged) {
        Object raw = merged.get("links");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> links = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    links.add((Map<String, Object>) map);
                }
            }
            return links;
        }
        List<Map<String, Object>> created = new ArrayList<>();
        merged.put("links", created);
        return created;
    }

    private Map<String, Object> normalizeLink(String appId, OperatorAgentScope scope, Map<String, Object> raw) {
        String path = stringValue(raw.get("path"));
        if (path.isBlank() || !isPathAllowed(path, scope)) {
            return null;
        }
        String kind = normalizeKind(stringValue(raw.get("kind")), path);
        String title = stringValue(raw.get("title"));
        if (title.isBlank()) {
            title = path.substring(path.lastIndexOf('.') + 1);
        }
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("kind", kind);
        link.put("path", path);
        link.put("title", title);
        link.put("url", operatorUrl(appId, kind, path));
        return link;
    }

    private static String normalizeKind(String kind, String path) {
        String normalized = kind.toLowerCase(Locale.ROOT);
        if ("report".equals(normalized) || "dashboard".equals(normalized)) {
            return normalized;
        }
        if (path.contains(".reports.")) {
            return "report";
        }
        return "dashboard";
    }

    static boolean isPathAllowed(String path, OperatorAgentScope scope) {
        if (scope == null || path == null || path.isBlank()) {
            return false;
        }
        for (String prefix : scope.pathPrefixes()) {
            if (path.equals(prefix) || path.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tableFromReport(String reportPath, Map<String, Object> reportData) {
        Object columnsRaw = reportData.get("columns");
        Object rowsRaw = reportData.get("rows");
        if (!(columnsRaw instanceof List<?> columns) || !(rowsRaw instanceof List<?> rows) || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("title", reportPath.substring(reportPath.lastIndexOf('.') + 1));
        table.put("reportPath", reportPath);
        table.put("columns", columns);
        table.put("rows", rows);
        table.put("rowCount", reportData.getOrDefault("rowCount", rows.size()));
        table.put("truncated", Boolean.TRUE.equals(reportData.get("truncated")));
        return table;
    }

    private static String linkKey(Map<String, Object> link) {
        return stringValue(link.get("kind")) + "|" + stringValue(link.get("path"));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
