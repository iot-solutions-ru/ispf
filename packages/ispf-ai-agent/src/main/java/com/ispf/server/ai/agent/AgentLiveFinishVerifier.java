package com.ispf.server.ai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Live-tree sensors for the pre-finish judge (ADR-0051 / harness verification).
 * Step logs alone are not enough — claimed finish paths must exist and carry widgets/bindings.
 */
final class AgentLiveFinishVerifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> PATH_LIST_KEYS = List.of(
            "devices", "dashboards", "mimics", "alerts", "users", "reports", "queries"
    );

    private static final List<String> PATH_SCALAR_KEYS = List.of(
            "device", "dashboard", "mimic", "alert", "report", "query",
            "eventFilter", "calculatorHub", "operatorApp"
    );

    private AgentLiveFinishVerifier() {
    }

    static List<String> verify(
            Map<String, Object> finishResult,
            List<Map<String, Object>> steps,
            String userMessage,
            Predicate<String> pathExists,
            Function<String, Optional<String>> dashboardLayoutJson,
            Function<String, Optional<String>> mimicDiagramJson
    ) {
        List<String> issues = new ArrayList<>();
        if (pathExists == null) {
            return issues;
        }
        Set<String> claimed = collectClaimedPaths(finishResult);
        if (claimed.isEmpty() && !looksLikeBuildTask(userMessage) && !hasCreateEvidence(steps)) {
            return issues;
        }
        if (claimed.isEmpty() && (looksLikeBuildTask(userMessage) || hasCreateEvidence(steps))) {
            claimed.addAll(pathsFromSuccessfulCreates(steps));
        }
        if (claimed.isEmpty()) {
            if (looksLikeBuildTask(userMessage)) {
                issues.add("live.paths: finish.result has no device/dashboard/mimic paths to verify");
            }
            return issues;
        }

        List<String> missing = new ArrayList<>();
        List<String> emptyDashboards = new ArrayList<>();
        List<String> unboundDashboards = new ArrayList<>();
        List<String> emptyMimics = new ArrayList<>();
        List<String> unboundMimics = new ArrayList<>();
        int liveDevices = 0;
        int liveDashboards = 0;
        int liveMimics = 0;

        for (String path : claimed) {
            if (!pathExists.test(path)) {
                missing.add(path);
                continue;
            }
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.contains(".devices.") || lower.endsWith(".devices")) {
                liveDevices++;
            }
            if (isDashboardPath(lower)) {
                liveDashboards++;
                Optional<String> layout = dashboardLayoutJson != null
                        ? dashboardLayoutJson.apply(path)
                        : Optional.empty();
                if (layout.isEmpty()) {
                    emptyDashboards.add(path + " (unreadable layout)");
                    continue;
                }
                int widgets = AgentToolResultMetrics.widgetCountFromLayoutJson(MAPPER, layout.get());
                if (widgets <= 0) {
                    emptyDashboards.add(path + " widgetCount=0");
                    continue;
                }
                int bindable = AgentToolResultMetrics.bindableWidgetCountFromLayoutJson(MAPPER, layout.get());
                int bound = AgentToolResultMetrics.boundWidgetCountFromLayoutJson(MAPPER, layout.get());
                if (bindable >= 2 && bound < Math.max(1, (bindable + 1) / 2)) {
                    unboundDashboards.add(path + " bound=" + bound + "/" + bindable);
                }
            }
            if (isMimicPath(lower)) {
                liveMimics++;
                Optional<String> diagram = mimicDiagramJson != null
                        ? mimicDiagramJson.apply(path)
                        : Optional.empty();
                if (diagram.isEmpty()) {
                    emptyMimics.add(path + " (unreadable diagram)");
                    continue;
                }
                int elements = elementCount(diagram.get());
                if (elements <= 0) {
                    emptyMimics.add(path + " elementCount=0");
                    continue;
                }
                int boundElements = boundElementCount(diagram.get());
                if (elements >= 2 && boundElements == 0) {
                    unboundMimics.add(path + " elements=" + elements + " bound=0");
                }
            }
        }

        if (!missing.isEmpty()) {
            issues.add("live.missing: finish claimed paths not in tree: " + String.join(", ", missing));
        }
        if (!emptyDashboards.isEmpty()) {
            issues.add("live.dashboard.empty: " + String.join("; ", emptyDashboards));
        }
        if (!unboundDashboards.isEmpty()) {
            issues.add("live.dashboard.bindings: " + String.join("; ", unboundDashboards));
        }
        if (!emptyMimics.isEmpty()) {
            issues.add("live.mimic.empty: " + String.join("; ", emptyMimics));
        }
        if (!unboundMimics.isEmpty()) {
            issues.add("live.mimic.bindings: " + String.join("; ", unboundMimics));
        }

        if (looksLikeCourse(userMessage)) {
            if (liveDevices < 2) {
                issues.add("live.course.devices: need ≥2 live devices for full course (found " + liveDevices + ")");
            }
            if (liveDashboards < 1) {
                issues.add("live.course.dashboards: need ≥1 live non-empty dashboard");
            }
            if (containsAny(userMessage, "мнемо", "mimic", "svg", "вентилятор", "scada") && liveMimics < 1) {
                issues.add("live.course.mimics: need ≥1 live non-empty mimic");
            }
        }

        Object completed = finishResult != null ? finishResult.get("completed") : null;
        if (completed instanceof Number number && number.intValue() >= 5) {
            int artifactCount = liveDevices + liveDashboards + liveMimics;
            if (!missing.isEmpty() || artifactCount < 3) {
                issues.add(
                        "live.completed: finish.result.completed=" + number.intValue()
                                + " but live artifacts are incomplete (devices=" + liveDevices
                                + ", dashboards=" + liveDashboards
                                + ", mimics=" + liveMimics + ")"
                );
            }
        }
        return issues;
    }

    static Set<String> collectClaimedPaths(Map<String, Object> finishResult) {
        Set<String> paths = new LinkedHashSet<>();
        if (finishResult == null || finishResult.isEmpty()) {
            return paths;
        }
        for (String key : PATH_LIST_KEYS) {
            addPaths(paths, finishResult.get(key));
        }
        for (String key : PATH_SCALAR_KEYS) {
            addPaths(paths, finishResult.get(key));
        }
        Object nested = finishResult.get("result");
        if (nested instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) map;
            paths.addAll(collectClaimedPaths(nestedMap));
        }
        return paths;
    }

    private static void addPaths(Set<String> out, Object raw) {
        if (raw instanceof String s && looksLikePath(s)) {
            out.add(s.trim());
            return;
        }
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof String s && looksLikePath(s)) {
                    out.add(s.trim());
                }
            }
        }
    }

    private static boolean looksLikePath(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.startsWith("root.") && trimmed.length() > 6 && !trimmed.contains(" ");
    }

    private static boolean isDashboardPath(String lowerPath) {
        return lowerPath.contains(".dashboards.") || lowerPath.endsWith(".dashboard");
    }

    private static boolean isMimicPath(String lowerPath) {
        return lowerPath.contains(".mimics.") || lowerPath.endsWith(".mimic");
    }

    private static boolean looksLikeBuildTask(String userMessage) {
        String text = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        // Explore/list prompts must not force live path claims.
        if (containsAny(text, "list ", "list_", "покажи", "show ", "explain", "объясни", "какие", "список")) {
            return false;
        }
        return containsAny(
                text,
                "создай", "create ", "create_", "deploy", "построй", "построить",
                "dashboard", "дашборд", "настрой", "configure", "virt-device", "мнемо", "mimic"
        );
    }

    private static boolean looksLikeCourse(String userMessage) {
        String text = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        return containsAny(text, "все задания", "выполняй все", "18", "virt-device", "aggregat")
                || (containsAny(text, "тревог") && containsAny(text, "дашборд") && containsAny(text, "мнемо", "mimic", "svg"));
    }

    private static boolean hasCreateEvidence(List<Map<String, Object>> steps) {
        return !pathsFromSuccessfulCreates(steps).isEmpty();
    }

    private static Set<String> pathsFromSuccessfulCreates(List<Map<String, Object>> steps) {
        Set<String> paths = new LinkedHashSet<>();
        if (steps == null) {
            return paths;
        }
        for (Map<String, Object> step : steps) {
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            String tool = String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT);
            Map<String, Object> result = step.get("result") instanceof Map<?, ?> map
                    ? castMap(map)
                    : Map.of();
            if (!"OK".equals(String.valueOf(result.get("status")))) {
                continue;
            }
            if ("create_object".equals(tool)
                    || "create_virtual_device".equals(tool)
                    || "configure_alert".equals(tool)
                    || "set_dashboard_layout".equals(tool)
                    || "save_mimic_diagram".equals(tool)) {
                Object path = result.get("path");
                if (path == null && step.get("arguments") instanceof Map<?, ?> args) {
                    path = args.get("path");
                }
                if (path instanceof String s && looksLikePath(s)) {
                    paths.add(s.trim());
                }
            }
        }
        return paths;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static int elementCount(String diagramJson) {
        try {
            JsonNode elements = MAPPER.readTree(diagramJson).path("elements");
            return elements.isArray() ? elements.size() : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    private static int boundElementCount(String diagramJson) {
        try {
            JsonNode elements = MAPPER.readTree(diagramJson).path("elements");
            if (!elements.isArray()) {
                return 0;
            }
            int bound = 0;
            for (JsonNode element : elements) {
                if (!element.isObject()) {
                    continue;
                }
                if (textPresent(element, "objectPath") && textPresent(element, "variableName")) {
                    bound++;
                    continue;
                }
                JsonNode bindings = element.path("bindings");
                if (bindings.isObject()) {
                    for (JsonNode binding : bindings) {
                        if (binding.isObject()
                                && textPresent(binding, "objectPath")
                                && textPresent(binding, "variableName")) {
                            bound++;
                            break;
                        }
                    }
                }
            }
            return bound;
        } catch (Exception ex) {
            return 0;
        }
    }

    private static boolean textPresent(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        return value != null && !value.isBlank();
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
