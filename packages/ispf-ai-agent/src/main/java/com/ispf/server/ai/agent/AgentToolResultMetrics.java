package com.ispf.server.ai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared parsing of agent tool result payloads — used by finish guards and dashboard/mimic tools
 * so validation always reads the same fields the tools emit.
 */
final class AgentToolResultMetrics {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Widget types that need objectPath + variableName to show live data. */
    private static final Set<String> DATA_BOUND_TYPES = Set.of(
            "value", "gauge", "chart", "sparkline", "history-chart", "history-table",
            "kpi", "radial-gauge", "bar", "line", "timeseries"
    );

    /** Widget types that need objectPath (events / forms / functions / tables). */
    private static final Set<String> OBJECT_BOUND_TYPES = Set.of(
            "event-feed", "object-table", "input-form", "function", "event-form", "properties"
    );

    private AgentToolResultMetrics() {
    }

    static int widgetCountFromLayoutJson(String layoutJson) {
        return widgetCountFromLayoutJson(MAPPER, layoutJson);
    }

    static int widgetCountFromLayoutJson(ObjectMapper objectMapper, String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return 0;
        }
        try {
            var root = objectMapper.readTree(layoutJson);
            return root.path("widgets").isArray() ? root.path("widgets").size() : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    static int boundWidgetCountFromLayoutJson(ObjectMapper objectMapper, String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode widgets = objectMapper.readTree(layoutJson).path("widgets");
            if (!widgets.isArray()) {
                return 0;
            }
            int bound = 0;
            for (JsonNode widget : widgets) {
                if (widget.isObject() && widgetIsBound(widget)) {
                    bound++;
                }
            }
            return bound;
        } catch (Exception ex) {
            return 0;
        }
    }

    static int bindableWidgetCountFromLayoutJson(ObjectMapper objectMapper, String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode widgets = objectMapper.readTree(layoutJson).path("widgets");
            if (!widgets.isArray()) {
                return 0;
            }
            int bindable = 0;
            for (JsonNode widget : widgets) {
                if (widget.isObject() && widgetNeedsBinding(widget)) {
                    bindable++;
                }
            }
            return bindable;
        } catch (Exception ex) {
            return 0;
        }
    }

    private static boolean widgetNeedsBinding(JsonNode widget) {
        String type = widget.path("type").asString("").trim().toLowerCase(Locale.ROOT);
        return DATA_BOUND_TYPES.contains(type)
                || OBJECT_BOUND_TYPES.contains(type)
                || "scada-mimic".equals(type)
                || "report".equals(type);
    }

    private static boolean widgetIsBound(JsonNode widget) {
        String type = widget.path("type").asString("").trim().toLowerCase(Locale.ROOT);
        if (!widgetNeedsBinding(widget)) {
            return true;
        }
        if ("scada-mimic".equals(type)) {
            return textPresent(widget, "mimicPath");
        }
        if ("report".equals(type)) {
            return textPresent(widget, "reportPath") || textPresent(widget, "reportId")
                    || textPresent(widget, "objectPath");
        }
        if (DATA_BOUND_TYPES.contains(type)) {
            return textPresent(widget, "objectPath") && textPresent(widget, "variableName");
        }
        return textPresent(widget, "objectPath");
    }

    private static boolean textPresent(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        return value != null && !value.isBlank();
    }

    static int widgetCountFromResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        Object widgetCount = result.get("widgetCount");
        if (widgetCount instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        Object widgets = result.get("widgets");
        if (widgets instanceof List<?> list && !list.isEmpty()) {
            return list.size();
        }
        Object layoutJson = result.get("layoutJson");
        if (layoutJson instanceof String json) {
            return widgetCountFromLayoutJson(json);
        }
        return 0;
    }

    static int elementCountFromResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return -1;
        }
        Object elementCount = result.get("elementCount");
        if (elementCount instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(elementCount));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Last OK elementCount from save_mimic_diagram or get_mimic_diagram in step order. */
    static int lastMimicElementCount(List<Map<String, Object>> steps) {
        Integer last = null;
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            if (!"get_mimic_diagram".equals(tool) && !"save_mimic_diagram".equals(tool)) {
                continue;
            }
            int count = elementCountFromResult(stepMap(step, "result"));
            if (count >= 0) {
                last = count;
            }
        }
        return last == null ? -1 : last;
    }

    /** Last OK widget count from dashboard layout tools in step order. */
    static int lastDashboardWidgetCount(List<Map<String, Object>> steps) {
        int last = 0;
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            if (!"get_dashboard_layout".equals(tool)
                    && !"set_dashboard_layout".equals(tool)
                    && !"add_dashboard_widget".equals(tool)) {
                continue;
            }
            int count = widgetCountFromResult(stepMap(step, "result"));
            if (count > 0) {
                last = count;
            }
        }
        return last;
    }

    /**
     * Per-dashboard path: max OK widgetCount vs last OK widgetCount in step order.
     * Used to catch destructive {@code set_dashboard_layout} that shrinks a full screen to crumbs.
     */
    static Map<String, int[]> dashboardWidgetCountPeaks(List<Map<String, Object>> steps) {
        Map<String, int[]> peaks = new LinkedHashMap<>();
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            if (!"get_dashboard_layout".equals(tool)
                    && !"set_dashboard_layout".equals(tool)
                    && !"add_dashboard_widget".equals(tool)) {
                continue;
            }
            Map<String, Object> result = stepMap(step, "result");
            Map<String, Object> args = stepMap(step, "arguments");
            String path = String.valueOf(result.getOrDefault("path", args.getOrDefault("path", ""))).trim();
            if (path.isBlank()) {
                continue;
            }
            int count = widgetCountFromResult(result);
            if (count <= 0) {
                continue;
            }
            int[] peak = peaks.computeIfAbsent(path, ignored -> new int[] {0, 0});
            peak[0] = Math.max(peak[0], count);
            peak[1] = count;
        }
        return peaks;
    }

    /**
     * Scan set_dashboard_layout arguments for unbound bindable widgets.
     * Returns {@code [bindable, bound]} across the last layoutJson per dashboard path.
     */
    static int[] dashboardBindingCounts(List<Map<String, Object>> steps) {
        Map<String, String> lastLayoutByPath = new LinkedHashMap<>();
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step) || !"set_dashboard_layout".equals(toolName(step))) {
                continue;
            }
            Map<String, Object> args = stepMap(step, "arguments");
            Map<String, Object> result = stepMap(step, "result");
            String path = String.valueOf(result.getOrDefault("path", args.getOrDefault("path", ""))).trim();
            if (path.isBlank()) {
                continue;
            }
            Object layout = args.get("layoutJson");
            if (layout == null) {
                layout = args.get("layout");
            }
            if (layout instanceof String json && !json.isBlank()) {
                lastLayoutByPath.put(path, json);
            } else if (layout != null && !(layout instanceof String)) {
                try {
                    lastLayoutByPath.put(path, MAPPER.writeValueAsString(layout));
                } catch (Exception ignored) {
                    // keep previous
                }
            }
        }
        int bindable = 0;
        int bound = 0;
        for (String layoutJson : lastLayoutByPath.values()) {
            bindable += bindableWidgetCountFromLayoutJson(MAPPER, layoutJson);
            bound += boundWidgetCountFromLayoutJson(MAPPER, layoutJson);
        }
        // Prefer tool-reported boundWidgetCount when layoutJson was not retained in steps.
        if (bindable == 0) {
            for (Map<String, Object> step : steps) {
                if (!isOkToolStep(step) || !"set_dashboard_layout".equals(toolName(step))) {
                    continue;
                }
                Map<String, Object> result = stepMap(step, "result");
                Object reported = result.get("boundWidgetCount");
                Object widgets = result.get("widgetCount");
                if (reported instanceof Number boundNum && widgets instanceof Number widgetNum) {
                    bound = Math.max(bound, boundNum.intValue());
                    bindable = Math.max(bindable, widgetNum.intValue());
                }
            }
        }
        return new int[] {bindable, bound};
    }

    static boolean hasVerifiedDashboardLayout(List<Map<String, Object>> steps) {
        return lastDashboardWidgetCount(steps) > 0;
    }

    static boolean hasVerifiedMimicDiagram(List<Map<String, Object>> steps) {
        return lastMimicElementCount(steps) > 0;
    }

    static int listVariablesMaxCount(List<Map<String, Object>> steps) {
        int max = 0;
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step) || !"list_variables".equals(toolName(step))) {
                continue;
            }
            Object count = stepMap(step, "result").get("count");
            if (count instanceof Number number) {
                max = Math.max(max, number.intValue());
            }
        }
        return max;
    }

    private static boolean isOkToolStep(Map<String, Object> step) {
        return "tool".equals(String.valueOf(step.get("type")))
                && "OK".equals(String.valueOf(stepMap(step, "result").get("status")));
    }

    private static String toolName(Map<String, Object> step) {
        return String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
