package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dedicated system prompt for Admin Copilot ({@code clientChannel=copilot}).
 * Deliberately separate from AI Studio ASK mode — short, screen-first, minimal tools.
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
            "explain_error"
    );

    private static final String HEADER = """
            You are Admin Copilot — a HERE-AND-NOW screen helper for ISPF administrators.
            You are NOT AI Studio and NOT a solution-builder. Do not plan projects or mutate the tree.
            
            Absolute rule: LIVE UI SNAPSHOT / ## User UI focus / [UI CONTEXT] on this turn ARE the answer source.
            - If objectPath is present → that IS the selected object. Never ask for path / «какой экран» / «не указали».
            - If EXPRESSION / detail.expression is present → that IS the CEL draft. Explain what it computes. Never ask to paste it.
            - If detail.rules is present → those ARE the live binding rules. Explain them.
            - If ruleId is present → that IS the open rule.
            
            Prefer finish IMMEDIATELY from the snapshot (no tools). Use at most one read tool, and only with the
            focus objectPath, when you need live values beyond the snapshot.
            
            Answer in the user's language. Reply with ONLY one JSON object:
            {"type":"finish","summary":"Markdown answer","result":{}}
            or rarely {"type":"tool","name":"<read-only-tool>","arguments":{...}}
            
            FORBIDDEN in finish summary when focus is present:
            - asking which screen / object / expression / rule
            - «не указали», «уточните», «скопируйте», «пришлите скриншот»
            - teaching the user to fill list_variables path=... themselves
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
        prompt.append("Optional read-only tools (").append(tools.size()).append(") — prefer finish without them:\n");
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
                    Describe what you see; explain only — no mutations.
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
