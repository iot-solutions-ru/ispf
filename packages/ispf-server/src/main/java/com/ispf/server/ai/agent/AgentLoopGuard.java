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

    static String continuationHint(String lastTool, List<Map<String, Object>> steps, int maxSteps) {
        if (steps == null || steps.isEmpty()) {
            return defaultHint(maxSteps, steps);
        }
        if (isRepeatedTool(lastTool, steps)) {
            return """
                    You called the same tool repeatedly. Change strategy or emit {"type":"finish",...}. \
                    For dashboards: get_dashboard_layout / set_dashboard_layout / add_dashboard_widget. \
                    Do not call search_context again for the same topic.""";
        }
        long recentSearch = recentToolCount(steps, "search_context");
        if (recentSearch >= REPEAT_THRESHOLD) {
            return """
                    Stop search_context. Use concrete platform tools (get_dashboard_layout, set_variable, \
                    configure_driver, add_dashboard_widget). Finish with {"type":"finish",...} when done.""";
        }
        return defaultHint(maxSteps, steps);
    }

    private static String defaultHint(int maxSteps, List<Map<String, Object>> steps) {
        int stepCount = steps != null ? steps.size() : 0;
        if (stepCount >= Math.max(1, maxSteps - 3)) {
            return "Step budget is almost exhausted — finish now with {\"type\":\"finish\",\"summary\":\"...\",\"result\":{...}}.";
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
}
