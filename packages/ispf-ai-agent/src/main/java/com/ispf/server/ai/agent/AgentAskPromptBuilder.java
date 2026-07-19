package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dedicated system prompt for {@link AgentInteractionMode#ASK} — read-only Q&amp;A, no planning pipeline.
 */
public final class AgentAskPromptBuilder {

    private static final String HEADER = """
            You are the ISPF platform assistant in ASK mode — read-only Q&A for admins.
            Answer in the UI locale from the Response language block at the top of this prompt.
            This mode NEVER plans projects, NEVER mutates the tree, NEVER emits phase=plan or result.plan.
            
            CRITICAL — screen context: if this turn includes LIVE UI SNAPSHOT, ## User UI focus, ## Client channel: Admin Copilot,
            [UI CONTEXT], or an EXPRESSION: block, that IS the screen/rule/expression the user means.
            Never ask «уточните выражение/правило/экран» when those fields are present — answer from them.
            
            Your job: explain platform concepts, document procedures, list/describe what exists, \
            preview reports, and answer «how do I…?» with step-by-step Markdown instructions.
            For real deploy/create/configure the user must switch to Execute or Plan mode — say so briefly when relevant.
            
            Work step-by-step with read-only tools when live data helps; otherwise answer from playbooks below.
            
            GROUND TRUTH: paths, appIds, report names — from tool results this turn, briefing, \
            OR from LIVE UI SNAPSHOT / ## User UI focus / [UI CONTEXT] on this turn (those ARE live state). \
            If objectPath or detail.rules appear in UI focus, that IS the object — never ask the user to type the path. \
            Playbook examples (mes-reference, pump-01) are patterns, not live state.
            
            How-to questions («как упаковать bundle?», «как это сделать?»):
            - Give numbered Markdown steps in summary (tool names in backticks).
            - Optional: list_applications, get_example_bundle appId=mes-reference, search_context.
            - Do NOT call import_package, configure_operator_ui, create_object, or other mutations.
            
            Reply with ONLY one JSON object per turn — no markdown fences:
            {"type":"tool","name":"<read-only-tool>","arguments":{...}}
            or when done:
            {"type":"finish","summary":"Markdown answer for the user","result":{}}
            
            result may include optional read-only suggestions (label + message) — never primary approval buttons.
            Do not call search_context more than 3 times in a row with the same query.
            
            """;

    private static final String FORMATTING = """
            FINISH SUMMARY FORMATTING (rendered as Markdown in chat):
            - Short intro, blank line, then numbered or bullet list.
            - One step per line; tool names in backticks; **bold** for step titles.
            - No markdown code fences in summary.
            
            """;

    private AgentAskPromptBuilder() {
    }

    public static String build(
            String rootPath,
            List<Map<String, Object>> fullToolCatalog,
            String platformBriefing,
            boolean hasImages,
            String sessionDocumentsSection
    ) {
        return build(rootPath, fullToolCatalog, platformBriefing, hasImages, sessionDocumentsSection, true);
    }

    /**
     * @param includePlaybooks when false (Admin Copilot with live UI focus), skip bulky reference playbooks
     *                         that drown the focus block and encourage generic clarifying questions.
     */
    public static String build(
            String rootPath,
            List<Map<String, Object>> fullToolCatalog,
            String platformBriefing,
            boolean hasImages,
            String sessionDocumentsSection,
            boolean includePlaybooks
    ) {
        String effectiveRoot = rootPath == null || rootPath.isBlank() ? "root" : rootPath.trim();
        List<Map<String, Object>> tools = readOnlyToolCatalog(fullToolCatalog);
        StringBuilder prompt = new StringBuilder(HEADER.length() + 4096);
        prompt.append(HEADER);
        prompt.append(FORMATTING);
        prompt.append("Default tree root: ").append(effectiveRoot).append("\n\n");
        if (platformBriefing != null && !platformBriefing.isBlank() && includePlaybooks) {
            prompt.append("## Platform knowledge (auto)\n");
            prompt.append(platformBriefing.trim()).append("\n\n");
        }
        if (sessionDocumentsSection != null && !sessionDocumentsSection.isBlank()) {
            prompt.append(sessionDocumentsSection.trim()).append("\n\n");
        }
        prompt.append("Read-only tools available in Ask mode (").append(tools.size()).append("):\n");
        for (Map<String, Object> tool : tools) {
            prompt.append("- ")
                    .append(tool.get("name"))
                    .append(": ")
                    .append(tool.get("description"))
                    .append("\n");
        }
        if (includePlaybooks) {
            prompt.append("\n## Reference playbooks (documentation — not live state)\n\n");
            prompt.append(AgentPlaybooks.applicationLifecycleGuide());
            prompt.append("\n\n");
            prompt.append(AgentPlaybooks.reportsGuide());
            prompt.append("\n\n");
            prompt.append(AgentPlaybooks.platformObjectTypesGuide());
        } else {
            prompt.append("\nUI focus is present for this Copilot turn — answer from LIVE UI SNAPSHOT / focus.\n")
                    .append("FORBIDDEN: asking for object path, screen, rule id, or expression when those are in focus.\n")
                    .append("If surface=binding and rules are listed, explain those rules (id, target, expression) directly.\n")
                    .append("Optional tools: describe_variables / list_binding_rules / list_variables using the focus objectPath — never ask the user for it.\n");
        }
        if (hasImages) {
            prompt.append("\n\n");
            prompt.append("""
                    ## IMAGE ATTACHMENT (this turn)
                    Describe what you see (P&ID, mockup, sketch). Ask mode: explain only — no mutations, no plan.
                    """);
        }
        return prompt.toString();
    }

    public static String build(
            String rootPath,
            List<Map<String, Object>> fullToolCatalog,
            String platformBriefing,
            boolean hasImages
    ) {
        return build(rootPath, fullToolCatalog, platformBriefing, hasImages, "", true);
    }

    static List<Map<String, Object>> readOnlyToolCatalog(List<Map<String, Object>> fullToolCatalog) {
        if (fullToolCatalog == null || fullToolCatalog.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> tool : fullToolCatalog) {
            Object name = tool.get("name");
            if (name != null && AgentPlanGuard.isReadOnlyTool(String.valueOf(name))) {
                filtered.add(tool);
            }
        }
        return List.copyOf(filtered);
    }
}
