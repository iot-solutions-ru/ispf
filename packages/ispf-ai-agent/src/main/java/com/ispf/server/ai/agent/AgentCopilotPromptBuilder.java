package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dedicated system prompt for Admin Copilot ({@code clientChannel=copilot}).
 * Completely separate from AI Studio interaction modes (Ask / Plan / Execute).
 */
public final class AgentCopilotPromptBuilder {

    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "list_objects",
            "get_object",
            "list_variables",
            "describe_variables",
            "list_binding_rules",
            "get_variable",
            "search_context",
            "list_events",
            "explain_error",
            "get_widget_catalog",
            "get_dashboard_layout",
            "add_dashboard_widget",
            "set_dashboard_layout",
            "create_binding_rule",
            "create_variable",
            "configure_variable_history",
            "list_mimic_symbols",
            "get_mimic_diagram",
            "save_mimic_diagram",
            "add_mimic_elements",
            "list_automation",
            "get_function",
            "list_functions"
    );

    /** Tree/doc discovery — unnecessary on System/Settings where LIVE UI already lists rows. */
    private static final Set<String> SETTINGS_SCREEN_SKIP_TOOLS = Set.of(
            "list_objects",
            "get_object",
            "search_context",
            "list_variables",
            "describe_variables",
            "list_binding_rules",
            "list_events",
            "list_automation",
            "list_functions",
            "get_function",
            "get_widget_catalog",
            "get_dashboard_layout",
            "list_mimic_symbols",
            "get_mimic_diagram",
            "explain_error"
    );

    private static final String HEADER = """
            You are Admin Copilot — a HERE-AND-NOW screen helper for ISPF administrators.
            You are NOT AI Studio. You have no Ask / Plan / Execute modes and must NEVER mention those modes,
            «переключитесь в режим», «Ask-режим», «Execute mode», or similar Studio mode advice.
            
            Absolute rule: LIVE UI SNAPSHOT / ## User UI focus / [UI CONTEXT] ARE the answer source.
            - objectPath / EXPRESSION / rules / widgetId / systemTab / mimic path fields ARE live — never ask which screen.
            - Help craft CEL, bindings, dashboard widgets, SCADA mimic elements for the focused screen.
            - When the user asks to fill / add / configure / create on this screen, USE tools to apply changes
              (e.g. save_mimic_diagram, add_mimic_elements, add_dashboard_widget, create_binding_rule) on the focus path.
            - 1-minute / rolling averages: create_binding_rule ruleKind=historian windowBucket=1m
              expression=avg(path/var, 1m) after configure_variable_history — NOT rolling-avg blueprints,
              NOT reactive CEL with invent read()/derivedValue/casts. Thresholds stay reactive on the avg var.
            - Prefer doing the work over meta UX advice.
            
            Workflow (understand first, then tools):
            1) Read the user question + LIVE UI SNAPSHOT and decide what they need.
            2) If the snapshot already answers (explain this screen / what a setting means / how Save-Refresh works):
               finish with Markdown — walk visibleSettingIds / fields in plain language. No Studio discovery tools.
            3) If you need live tree values to configure something on this focus path: 1–3 read tools, then mutate.
            4) Never invent devices, dashboards, or “finish checks”. Never call get_automation_schema /
               search_context / list_objects just to “look around” when the snapshot already has the screen.
            
            Answer ONLY in the UI locale from the Response language block (and FINAL REMINDER) in this prompt.
            You are Admin Copilot — never introduce yourself as the "ISPF platform agent" / Studio builder,
            and never open with a canned multi-bullet onboarding in the wrong language.
            For a bare hello/hi: one short greeting + what you can do on THIS focused screen, in the UI locale.
            
            Reply with ONLY one JSON object:
            {"type":"finish","summary":"Markdown answer","result":{}}
            or {"type":"tool","name":"<tool>","arguments":{...}}
            
            FORBIDDEN in finish:
            - mentioning Ask / Plan / Execute / «режим Спросить» / «режим Выполнить»
            - asking which screen / object / expression when focus already has it
            - «не указали», «уточните», «скопируйте», «пришлите скриншот»
            - answering in Russian (or any non-UI language) when the UI locale is English/de/zh
            """;

    private AgentCopilotPromptBuilder() {
    }

    public static String build(
            String rootPath,
            List<Map<String, Object>> fullToolCatalog,
            boolean hasImages
    ) {
        return build(rootPath, fullToolCatalog, hasImages, null);
    }

    public static String build(
            String rootPath,
            List<Map<String, Object>> fullToolCatalog,
            boolean hasImages,
            Map<String, Object> clientFocus
    ) {
        String effectiveRoot = rootPath == null || rootPath.isBlank() ? "root" : rootPath.trim();
        List<Map<String, Object>> tools = filterTools(fullToolCatalog, clientFocus);
        StringBuilder prompt = new StringBuilder(HEADER.length() + 2048);
        prompt.append(HEADER);
        prompt.append("\nDefault tree root: ").append(effectiveRoot).append("\n");
        prompt.append("Tools for screen help (").append(tools.size()).append("):\n");
        for (Map<String, Object> tool : tools) {
            prompt.append("- ")
                    .append(tool.get("name"))
                    .append(": ")
                    .append(tool.get("description"))
                    .append('\n');
        }
        if (hasImages) {
            prompt.append("""
                    
                    ## IMAGE ATTACHMENT
                    Describe what you see; use it to help configure the focused screen.
                    """);
        }
        return prompt.toString();
    }

    static List<Map<String, Object>> filterTools(List<Map<String, Object>> fullToolCatalog) {
        return filterTools(fullToolCatalog, null);
    }

    static List<Map<String, Object>> filterTools(
            List<Map<String, Object>> fullToolCatalog,
            Map<String, Object> clientFocus
    ) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (fullToolCatalog == null) {
            return out;
        }
        boolean settingsScreen = isSettingsScreenFocus(clientFocus);
        for (Map<String, Object> tool : fullToolCatalog) {
            if (tool == null) {
                continue;
            }
            Object name = tool.get("name");
            if (name == null) {
                continue;
            }
            String key = String.valueOf(name).trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_TOOLS.contains(key)) {
                continue;
            }
            if (settingsScreen && SETTINGS_SCREEN_SKIP_TOOLS.contains(key)) {
                continue;
            }
            out.add(tool);
        }
        return out;
    }

    static boolean isAllowedTool(String toolName) {
        return isAllowedTool(toolName, null);
    }

    static boolean isAllowedTool(String toolName, Map<String, Object> clientFocus) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String key = toolName.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TOOLS.contains(key)) {
            return false;
        }
        return !(isSettingsScreenFocus(clientFocus) && SETTINGS_SCREEN_SKIP_TOOLS.contains(key));
    }

    static boolean isSettingsScreenFocus(Map<String, Object> clientFocus) {
        if (clientFocus == null || clientFocus.isEmpty()) {
            return false;
        }
        Object detail = clientFocus.get("detail");
        if (!(detail instanceof Map<?, ?> detailMap)) {
            return false;
        }
        String systemTab = stringVal(detailMap.get("systemTab"));
        String settingsTab = stringVal(detailMap.get("settingsTab"));
        if (!settingsTab.isBlank() || "settings".equalsIgnoreCase(systemTab)) {
            return true;
        }
        Object ids = detailMap.get("visibleSettingIds");
        return ids instanceof List<?> list && !list.isEmpty();
    }

    private static String stringVal(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
