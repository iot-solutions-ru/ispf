package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class AgentLoopGuard {

    private static final int RECENT_WINDOW = 5;
    private static final int REPEAT_THRESHOLD = 3;

    private AgentLoopGuard() {
    }

    static String continuationHint(
            String lastTool,
            List<Map<String, Object>> steps,
            int maxStepsTotal
    ) {
        if (steps == null || steps.isEmpty()) {
            return defaultHint(steps, maxStepsTotal);
        }
        if ("create_object".equals(lastTool) && lastStepGroundTruthBlocked(steps)) {
            String blockedParent = blockedParentPath(steps);
            return "Parent path not discovered for create_object. "
                    + "Call list_objects parent=" + blockedParent
                    + " first (exact folder), verify objects[].path in the result, then retry create_object.";
        }
        if ("run_report".equals(lastTool)) {
            return """
                    Report data received. Prefer finish with a summary if the goal is met; \
                    otherwise continue with one more targeted read tool.""";
        }
        if ("create_object".equals(lastTool)) {
            return """
                    Object created. For virtual simulators use create_virtual_device next time. \
                    If driverId=virtual: set templateId from list_relative_models OR recreate with create_virtual_device; \
                    set driverConfigJson profile from list_virtual_profiles, configure_driver autoStart=true, list_variables before finish.""";
        }
        if ("search_platform_recipes".equals(lastTool) || "get_automation_schema".equals(lastTool)) {
            return """
                    Recipe/schema loaded — this is a pattern, not live tree state. \
                    Next: list_objects on the real parent folder, list_relative_models / list_virtual_profiles, \
                    then create using paths and modelName from those tool results only.""";
        }
        if ("create_virtual_device".equals(lastTool)) {
            return """
                    Virtual device provisioned. Verify telemetryVariableCount>0; bind SCADA/dashboard using returned variable names; \
                    list_variables on each device before finish.""";
        }
        if (isRepeatedTool(lastTool, steps)) {
            return """
                    You called the same tool repeatedly. Change strategy or emit {"type":"finish",...}. \
                    If the user's intent is unclear, prefer finish with a short question and result.suggestions \
                    (label + message per option, interactive=true) instead of more blind tool calls. \
                    For dashboards: list_variables first; use set_dashboard_layout template= instead of many add_dashboard_widget; \
                    never set_variable name=widgets. get_widget_catalog type=<type> for exact widget fields. \
                    For SCADA mimics: save_mimic_diagram or add_mimic_elements with non-empty elements[]; \
                    list_mimic_symbols for symbolId; get_mimic_diagram to verify elementCount; never set_variable name=diagram. \
                    For platform docs: list_drivers, get_driver_help, get_example_bundle, get_automation_schema instead of search_context loops. \
                    Before invoke_bff: use list_functions and get_function instead of guessing function names.""";
        }
        long recentSearch = recentToolCount(steps, "search_context");
        if (recentSearch >= REPEAT_THRESHOLD) {
            return """
                    Stop search_context loops. Use list_drivers, get_driver_help, list_examples, \
                    get_example_bundle, or concrete tree tools (set_variable, configure_driver). \
                    Finish with {"type":"finish",...} when done.""";
        }
        return defaultHint(steps, maxStepsTotal);
    }

    private static String defaultHint(List<Map<String, Object>> steps, int maxStepsTotal) {
        int stepCount = steps != null ? steps.size() : 0;
        int remainingTotal = Math.max(0, maxStepsTotal - stepCount);
        if (remainingTotal <= 3) {
            return "Step limit almost reached (" + stepCount + "/" + maxStepsTotal
                    + ") — finish now with {\"type\":\"finish\",\"summary\":\"...\",\"result\":{...}}.";
        }
        return "Continue with another tool action or finish when the goal is complete.";
    }

    private static boolean isRepeatedTool(String lastTool, List<Map<String, Object>> steps) {
        if (lastTool == null || lastTool.isBlank()) {
            return false;
        }
        return recentToolCount(steps, lastTool) >= REPEAT_THRESHOLD;
    }

    private static long recentToolCount(List<Map<String, Object>> steps, String toolName) {
        int start = Math.max(0, steps.size() - RECENT_WINDOW);
        return steps.stream()
                .skip(start)
                .filter(step -> "tool".equals(String.valueOf(step.get("type"))))
                .map(step -> step.get("tool"))
                .filter(Objects::nonNull)
                .map(tool -> String.valueOf(tool).toLowerCase(Locale.ROOT))
                .filter(tool -> tool.equals(toolName.toLowerCase(Locale.ROOT)))
                .count();
    }

    private static boolean lastStepGroundTruthBlocked(List<Map<String, Object>> steps) {
        if (steps.isEmpty()) {
            return false;
        }
        Map<String, Object> last = steps.get(steps.size() - 1);
        if (!"tool".equals(String.valueOf(last.get("type")))) {
            return false;
        }
        Map<String, Object> result = stepMap(last, "result");
        if (!"ERROR".equals(String.valueOf(result.get("status")))) {
            return false;
        }
        String error = String.valueOf(result.get("error"));
        return error.contains("not discovered in this turn");
    }

    private static String blockedParentPath(List<Map<String, Object>> steps) {
        Map<String, Object> last = steps.get(steps.size() - 1);
        String error = String.valueOf(stepMap(last, "result").get("error"));
        int marker = error.lastIndexOf(": ");
        if (marker >= 0 && marker + 2 < error.length()) {
            return error.substring(marker + 2).trim();
        }
        return "root.platform.mimics";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
