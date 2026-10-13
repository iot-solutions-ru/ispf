package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Detects read-only «explain this screen» Copilot turns that must finish from LIVE UI
 * without Studio discovery tools or AcceptanceVerdict finish checks.
 */
final class AgentCopilotExplainIntent {

    private static final Set<String> DISCOVERY_TOOLS = Set.of(
            "list_objects",
            "get_object",
            "search_objects",
            "search_context",
            "list_variables",
            "describe_variables",
            "list_binding_rules",
            "list_events",
            "list_functions",
            "get_function",
            "list_automation",
            "get_widget_catalog",
            "get_dashboard_layout",
            "list_mimic_symbols",
            "get_mimic_diagram",
            "explain_error"
    );

    private AgentCopilotExplainIntent() {
    }

    static boolean isExplainOnly(String userMessage, Map<String, Object> clientFocus) {
        if (!hasScreenFocus(clientFocus)) {
            return false;
        }
        String text = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        if (looksLikeMutate(text)) {
            return false;
        }
        return containsAny(
                text,
                "explain",
                "what is",
                "what's",
                "whats",
                "what does",
                "what do",
                "how does",
                "tell me about",
                "describe",
                "объясн",
                "расскаж",
                "что это",
                "что тут",
                "что на",
                "зачем",
                "как работ",
                "для чего",
                "что означа",
                "что делает",
                "что значат",
                "erklä",
                "was ist",
                "was macht",
                "说明",
                "解释",
                "这是什么",
                "做什么"
        );
    }

    static boolean shouldBlockDiscoveryTool(String toolName, String userMessage, Map<String, Object> clientFocus) {
        if (!isExplainOnly(userMessage, clientFocus)) {
            return false;
        }
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        return DISCOVERY_TOOLS.contains(toolName.trim().toLowerCase(Locale.ROOT));
    }

    static String discoveryBlockHint(Map<String, Object> clientFocus) {
        StringBuilder hint = new StringBuilder();
        hint.append("This is an explain-screen turn. Do NOT call discovery tools.\n");
        hint.append("Finish NOW with Markdown that explains THIS focused screen from LIVE UI SNAPSHOT.\n");
        if (clientFocus != null) {
            Object detail = clientFocus.get("detail");
            if (detail instanceof Map<?, ?> detailMap) {
                Object ids = detailMap.get("visibleSettingIds");
                if (ids instanceof List<?> list && !list.isEmpty()) {
                    hint.append("Walk visibleSettingIds one by one in plain language: ");
                    int limit = Math.min(list.size(), 16);
                    for (int i = 0; i < limit; i++) {
                        if (i > 0) {
                            hint.append(", ");
                        }
                        hint.append(String.valueOf(list.get(i)));
                    }
                    hint.append(".\n");
                }
            }
        }
        hint.append("Reply: {\"type\":\"finish\",\"summary\":\"...\",\"result\":{}}");
        return hint.toString();
    }

    private static boolean hasScreenFocus(Map<String, Object> clientFocus) {
        if (clientFocus == null || clientFocus.isEmpty()) {
            return false;
        }
        String surface = stringVal(clientFocus.get("surface"));
        if ("system".equalsIgnoreCase(surface) || "ai-studio".equalsIgnoreCase(surface)) {
            return true;
        }
        Object detail = clientFocus.get("detail");
        if (!(detail instanceof Map<?, ?> detailMap)) {
            return false;
        }
        return !stringVal(detailMap.get("screenTitle")).isBlank()
                || !stringVal(detailMap.get("systemTab")).isBlank()
                || !stringVal(detailMap.get("settingsTab")).isBlank()
                || !stringVal(detailMap.get("studioTab")).isBlank()
                || (detailMap.get("visibleSettingIds") instanceof List<?> ids && !ids.isEmpty());
    }

    private static boolean looksLikeMutate(String text) {
        return containsAny(
                text,
                "create",
                "add ",
                "set ",
                "enable",
                "disable",
                "configure",
                "change",
                "update",
                "save",
                "apply",
                "fill",
                "deploy",
                "созда",
                "добав",
                "настро",
                "включи",
                "выключи",
                "измен",
                "сохран",
                "заполни",
                "примен"
        );
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String stringVal(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
