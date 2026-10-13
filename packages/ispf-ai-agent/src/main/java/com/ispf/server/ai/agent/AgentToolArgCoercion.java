package com.ispf.server.ai.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Best-effort argument normalization before schema validation (Qwen/native tool-call quirks).
 */
final class AgentToolArgCoercion {

    private AgentToolArgCoercion() {
    }

    static Map<String, Object> coerce(String toolName, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        if (toolName == null || toolName.isBlank()) {
            return args;
        }
        String normalized = toolName.trim().toLowerCase(Locale.ROOT);
        if ("enable_agent_tool_pack".equals(normalized)) {
            return coerceEnableAgentToolPack(args);
        }
        if ("configure_report".equals(normalized)) {
            return coerceConfigureReport(args);
        }
        if ("configure_alert".equals(normalized)) {
            return coerceConfigureAlert(args);
        }
        if ("configure_correlator".equals(normalized)) {
            return coerceConfigureCorrelator(args);
        }
        return args;
    }

    private static Map<String, Object> coerceConfigureReport(Map<String, Object> args) {
        Map<String, Object> copy = new LinkedHashMap<>(args);
        String path = stringValue(copy.get("path"));
        if (!path.isBlank()) {
            return copy;
        }
        String reportId = stringValue(copy.get("reportId"));
        if (reportId.isBlank()) {
            reportId = stringValue(copy.get("name"));
        }
        if (reportId.isBlank()) {
            reportId = stringValue(copy.get("id"));
        }
        if (reportId.isBlank()) {
            return copy;
        }
        if (reportId.startsWith("root.")) {
            copy.put("path", reportId);
        } else {
            copy.put("path", "root.platform.reports." + reportId);
            copy.putIfAbsent("reportId", reportId);
        }
        return copy;
    }

    private static Map<String, Object> coerceConfigureAlert(Map<String, Object> args) {
        Map<String, Object> copy = new LinkedHashMap<>(args);
        String path = stringValue(copy.get("path"));
        if (!path.isBlank()) {
            return copy;
        }
        String alertId = stringValue(copy.get("alertId"));
        if (alertId.isBlank()) {
            alertId = stringValue(copy.get("name"));
        }
        if (alertId.isBlank()) {
            alertId = stringValue(copy.get("id"));
        }
        if (alertId.isBlank()) {
            return copy;
        }
        if (alertId.startsWith("root.")) {
            copy.put("path", alertId);
        } else {
            // Keep name for create-on-configure; path helps schema/grounding tooling.
            copy.putIfAbsent("name", alertId);
            copy.put("path", "root.platform.alert-rules." + slugifyAlert(alertId));
        }
        return copy;
    }

    private static Map<String, Object> coerceConfigureCorrelator(Map<String, Object> args) {
        Map<String, Object> copy = new LinkedHashMap<>(args);
        String path = stringValue(copy.get("path"));
        if (!path.isBlank()) {
            return copy;
        }
        String correlatorId = stringValue(copy.get("correlatorId"));
        if (correlatorId.isBlank()) {
            correlatorId = stringValue(copy.get("name"));
        }
        if (correlatorId.isBlank()) {
            correlatorId = stringValue(copy.get("id"));
        }
        if (correlatorId.isBlank()) {
            return copy;
        }
        if (correlatorId.startsWith("root.")) {
            copy.put("path", correlatorId);
        } else {
            copy.putIfAbsent("name", correlatorId);
            copy.put("path", "root.platform.correlators." + slugifyAlert(correlatorId));
        }
        return copy;
    }

    private static String slugifyAlert(String value) {
        String slug = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "alert" : slug;
    }

    private static Map<String, Object> coerceEnableAgentToolPack(Map<String, Object> args) {
        Map<String, Object> copy = new LinkedHashMap<>(args);
        String pack = stringValue(copy.get("pack"));
        if (!pack.isBlank()) {
            copy.put("pack", pack.toLowerCase(Locale.ROOT));
            return copy;
        }
        for (String alt : List.of("name", "toolPack", "packId", "packName", "id", "value")) {
            String candidate = stringValue(copy.get(alt));
            if (candidate.isBlank()) {
                continue;
            }
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (AgentToolPackCatalog.ALL_PACKS.contains(lower)) {
                copy.put("pack", lower);
                return copy;
            }
            // Model sometimes passes a tool name; map to its pack.
            String fromTool = AgentToolPackCatalog.packFor(lower);
            if (fromTool != null
                    && !fromTool.isBlank()
                    && !AgentToolPackCatalog.CORE.equals(fromTool)
                    && AgentToolPackCatalog.ALL_PACKS.contains(fromTool)) {
                copy.put("pack", fromTool);
                return copy;
            }
        }
        return copy;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
