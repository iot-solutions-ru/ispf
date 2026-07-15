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
            
            Workflow:
            1) Prefer finish from the snapshot with concrete Markdown (CEL snippets, field patches) when no apply is needed.
            2) Gather with 1–3 read tools using focus paths when live values help.
            3) Mutate with tools when the user wants the change applied on the current screen.
            
            Answer in the user's language. Reply with ONLY one JSON object:
            {"type":"finish","summary":"Markdown answer","result":{}}
            or {"type":"tool","name":"<tool>","arguments":{...}}
            
            FORBIDDEN in finish:
            - mentioning Ask / Plan / Execute / «режим Спросить» / «режим Выполнить»
            - asking which screen / object / expression when focus already has it
            - «не указали», «уточните», «скопируйте», «пришлите скриншот»
            """;

    private AgentCopilotPromptBuilder() {
    }

    public static String build(
            String rootPath,
            List<Map<String, Object>> fullToolCatalog,
            boolean hasImages
    ) {
        String effectiveRoot = rootPath == null || rootPath.isBlank() ? "root" : rootPath.trim();
        List<Map<String, Object>> tools = filterTools(fullToolCatalog);
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
        List<Map<String, Object>> out = new ArrayList<>();
        if (fullToolCatalog == null) {
            return out;
        }
        for (Map<String, Object> tool : fullToolCatalog) {
            if (tool == null) {
                continue;
            }
            Object name = tool.get("name");
            if (name == null) {
                continue;
            }
            String key = String.valueOf(name).trim().toLowerCase(Locale.ROOT);
            if (ALLOWED_TOOLS.contains(key)) {
                out.add(tool);
            }
        }
        return out;
    }
}
