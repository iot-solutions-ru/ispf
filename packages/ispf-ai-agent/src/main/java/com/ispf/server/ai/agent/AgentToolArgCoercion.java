package com.ispf.server.ai.agent;

import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Best-effort argument normalization before schema validation (Qwen/native tool-call quirks).
 * Studio metrics: many ERROR rates came from schema requiring keys that execute already aliases.
 */
final class AgentToolArgCoercion {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentToolArgCoercion() {
    }

    static Map<String, Object> coerce(String toolName, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        if (toolName == null || toolName.isBlank()) {
            return args;
        }
        String normalized = toolName.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "enable_agent_tool_pack", "enable_pack" -> coerceEnableAgentToolPack(args);
            case "configure_report" -> coerceConfigureReport(args);
            case "configure_alert" -> coerceConfigureAlert(args);
            case "configure_correlator" -> coerceConfigureCorrelator(args);
            case "search_context" -> coerceSearchContext(args);
            case "ensure_singleton_instance" -> coerceBlueprintId(args);
            case "apply_mixin_blueprint" -> coerceApplyMixin(args);
            case "create_binding_rule" -> coerceCreateBindingRule(args);
            case "add_dashboard_widget" -> coerceAddDashboardWidget(args);
            case "deploy_tree_function" -> coerceDeployTreeFunction(args);
            case "save_mimic_diagram", "add_mimic_elements", "get_mimic_diagram" ->
                    aliasPath(args, "mimicPath", "objectPath", "dashboardPath");
            case "get_dashboard_layout", "set_dashboard_layout" ->
                    aliasPath(args, "dashboardPath", "objectPath", "mimicPath");
            case "get_object", "list_variables", "describe_variables", "create_variable",
                 "configure_variable_history", "set_variable" ->
                    aliasPath(args, "objectPath", "devicePath");
            default -> args;
        };
    }

    /**
     * Accept topic-only / alias keys that Studio agents emit (empty {@code query} was ~90% ERROR).
     */
    private static Map<String, Object> coerceSearchContext(Map<String, Object> args) {
        Map<String, Object> copy = new LinkedHashMap<>(args);
        String query = stringValue(copy.get("query"));
        if (query.isBlank()) {
            for (String alt : List.of("q", "text", "search", "keywords")) {
                String candidate = stringValue(copy.get(alt));
                if (!candidate.isBlank()) {
                    copy.put("query", candidate);
                    query = candidate;
                    break;
                }
            }
        }
        String topic = stringValue(copy.get("topic"));
        if (query.isBlank() && !topic.isBlank() && !"all".equalsIgnoreCase(topic)) {
            copy.put("query", topic);
        }
        return copy;
    }

    private static Map<String, Object> coerceBlueprintId(Map<String, Object> args) {
        Map<String, Object> copy = new LinkedHashMap<>(args);
        if (!stringValue(copy.get("blueprintId")).isBlank()) {
            return copy;
        }
        for (String alt : List.of("blueprintName", "name", "id", "model", "templateId", "modelName")) {
            String candidate = stringValue(copy.get(alt));
            if (!candidate.isBlank()) {
                copy.put("blueprintId", candidate);
                return copy;
            }
        }
        return copy;
    }

    private static Map<String, Object> coerceApplyMixin(Map<String, Object> args) {
        Map<String, Object> copy = aliasPathTo(args, "objectPath", "path", "devicePath");
        return coerceBlueprintId(copy);
    }

    private static Map<String, Object> coerceCreateBindingRule(Map<String, Object> args) {
        Map<String, Object> copy = aliasPath(args, "objectPath", "devicePath");
        mergeNestedPayload(copy, "ruleJson", "rule", "bindingRule");
        if (stringValue(copy.get("targetVariable")).isBlank()
                && stringValue(copy.get("targetRef")).isBlank()) {
            String id = stringValue(copy.get("id"));
            if (!id.isBlank()) {
                copy.put("targetVariable", id);
            }
        }
        return copy;
    }

    private static Map<String, Object> coerceAddDashboardWidget(Map<String, Object> args) {
        Map<String, Object> copy = aliasPath(args, "dashboardPath", "objectPath");
        Object widget = copy.get("widget");
        if (widget instanceof String json && !json.isBlank()) {
            Object parsed = tryParseJson(json);
            if (parsed instanceof Map<?, ?> map) {
                copy.put("widget", new LinkedHashMap<>(castMap(map)));
            }
        }
        return copy;
    }

    private static Map<String, Object> coerceDeployTreeFunction(Map<String, Object> args) {
        Map<String, Object> copy = aliasPath(args, "objectPath", "hostPath");
        parseSchemaObjectField(copy, "inputSchema");
        parseSchemaObjectField(copy, "outputSchema");
        String hostKind = stringValue(copy.get("hostKind")).toUpperCase(Locale.ROOT);
        if (hostKind.isBlank()) {
            // Only copy explicit SINGLETON/INSTANCE aliases — never invent a default (DEVICE stays rejected).
            String hint = stringValue(copy.get("logicHostKind"));
            if (hint.isBlank()) {
                hint = stringValue(copy.get("kind"));
            }
            if ("INSTANCE".equalsIgnoreCase(hint) || "SINGLETON".equalsIgnoreCase(hint)) {
                copy.put("hostKind", hint.toUpperCase(Locale.ROOT));
            }
        } else {
            copy.put("hostKind", hostKind);
        }
        return copy;
    }

    private static void parseSchemaObjectField(Map<String, Object> copy, String key) {
        Object value = copy.get(key);
        if (value instanceof String json && !json.isBlank()) {
            Object parsed = tryParseJson(json);
            if (parsed instanceof Map<?, ?> map) {
                copy.put(key, new LinkedHashMap<>(castMap(map)));
            }
        }
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
        mergeNestedPayload(copy, "configJson", "config", "alert");
        // Nested config often reuses device path as "path" — keep only real alert-rule paths.
        String path = stringValue(copy.get("path"));
        if (!path.isBlank() && !path.startsWith("root.platform.alert-rules.")) {
            copy.remove("path");
            path = "";
        }
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
        mergeNestedPayload(copy, "configJson", "config", "correlator");
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

    /** Merge nested Map/JSON payload into flat args without overwriting existing keys. */
    private static void mergeNestedPayload(Map<String, Object> copy, String... keys) {
        for (String key : keys) {
            Object raw = copy.get(key);
            Map<String, Object> nested = asStringKeyedMap(raw);
            if (nested == null || nested.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Object> entry : nested.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                copy.putIfAbsent(entry.getKey(), entry.getValue());
            }
            copy.remove(key);
            return;
        }
    }

    private static Map<String, Object> asStringKeyedMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return castMap(map);
        }
        if (raw instanceof String json && !json.isBlank()) {
            Object parsed = tryParseJson(json);
            if (parsed instanceof Map<?, ?> map) {
                return castMap(map);
            }
        }
        return null;
    }

    private static Object tryParseJson(String json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> aliasPath(Map<String, Object> args, String... aliases) {
        return aliasPathTo(args, "path", aliases);
    }

    private static Map<String, Object> aliasPathTo(Map<String, Object> args, String targetKey, String... aliases) {
        Map<String, Object> copy = new LinkedHashMap<>(args);
        if (!stringValue(copy.get(targetKey)).isBlank()) {
            return copy;
        }
        for (String alt : aliases) {
            String candidate = stringValue(copy.get(alt));
            if (!candidate.isBlank()) {
                copy.put(targetKey, candidate);
                return copy;
            }
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return out;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
