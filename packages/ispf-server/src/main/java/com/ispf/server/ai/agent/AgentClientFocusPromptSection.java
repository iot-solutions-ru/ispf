package com.ispf.server.ai.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Formats optional UI client-focus metadata into a short system-prompt section so the
 * admin agent can tailor answers to the screen the user is looking at.
 */
public final class AgentClientFocusPromptSection {

    private AgentClientFocusPromptSection() {
    }

    /**
     * Bias the agent for Admin Copilot (screen help) vs AI Studio (build solutions).
     * Put this near the top of the system prompt — ASK mode has a large tool/playbook body.
     */
    public static String formatChannel(String clientChannel) {
        if (clientChannel == null || clientChannel.isBlank()) {
            return "";
        }
        return switch (clientChannel.trim().toLowerCase()) {
            case "copilot" -> """
                    ## Client channel: Admin Copilot (dedicated screen helper — not AI Studio)
                    Answer ONLY about the screen in LIVE UI SNAPSHOT / User UI focus / [UI CONTEXT].
                    objectPath / EXPRESSION / rules / ruleId / widget / mimic fields ARE ground truth.
                    Never ask which screen/path/expression. Never mention Ask / Plan / Execute modes.
                    When asked to fill or configure the open screen, apply with tools — do not tell the user to switch modes.
                    """;
            case "studio" -> """
                    ## Client channel: AI Studio
                    You are in the solution-building workspace (Platform Studio).
                    Prefer tree-first plan/build/deploy. Do not assume explorer selection is the task unless the user mentions it.
                    """;
            default -> "";
        };
    }

    public static String format(Map<String, Object> clientFocus) {
        if (clientFocus == null || clientFocus.isEmpty()) {
            return "";
        }
        String surface = stringVal(clientFocus.get("surface"));
        if (surface.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## User UI focus (authoritative — use this screen)\n");
        sb.append("The administrator is focused on this surface. Questions about «this screen» / «here» / «этом экране» / «это правило» / «это выражение» refer to it.\n");
        sb.append("- surface: ").append(surface).append('\n');
        appendIfPresent(sb, "objectPath", clientFocus.get("objectPath"));
        appendIfPresent(sb, "objectType", clientFocus.get("objectType"));
        appendIfPresent(sb, "editorTabId", clientFocus.get("editorTabId"));
        Object detail = clientFocus.get("detail");
        if (detail instanceof Map<?, ?> detailMap && !detailMap.isEmpty()) {
            appendTrail(sb, detailMap.get("trail"));
            appendRecentActions(sb, detailMap.get("recentActions"));
            appendRulesList(sb, detailMap.get("rules"), "  ");
            sb.append("- detail:\n");
            for (Map.Entry<?, ?> entry : detailMap.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = String.valueOf(entry.getKey()).trim();
                if (key.isEmpty()
                        || "trail".equals(key)
                        || "recentActions".equals(key)
                        || "rules".equals(key)) {
                    continue;
                }
                String value = Objects.toString(entry.getValue(), "");
                if (value.length() > 800) {
                    value = value.substring(0, 800) + "…";
                }
                sb.append("  - ").append(key).append(": ").append(value).append('\n');
            }
        }
        switch (surface) {
            case "expression-editor" -> sb.append(
                    "Guidance: expression editor is open. detail.expression is the LIVE draft CEL. "
                            + "Your finish MUST explain that CEL line (what it computes) in context of "
                            + "ruleId/target/objectPath/trail. "
                            + "FORBIDDEN: generic editor handbook, «пришлите выражение/скриншот», "
                            + "or asking which expression — it is already in detail.expression.\n"
            );
            case "binding-rule" -> sb.append(
                    "Guidance: binding/computation rule editor is open. detail.ruleId / expression / target describe "
                            + "THE rule. Answer «о чем это правило?» from these fields.\n"
            );
            case "binding" -> sb.append(
                    "Guidance: computations/bindings list is open for objectPath. "
                            + "detail.rules (also listed above) ARE the live rules — explain them; "
                            + "do not ask which object or which rules.\n"
            );
            case "properties" -> sb.append(
                    "Guidance: object inspector is open. detail.inspectorTab is the active tab; "
                            + "detail.availableTabs lists what they can open. Explain that tab for objectPath.\n"
            );
            case "dashboard" -> sb.append(
                    "Guidance: dashboard editor/view is focused. detail.widgetId/widgetType/dataBinding/fields "
                            + "describe the SELECTED widget when present. Help configure fields, bindings, "
                            + "or draft add_dashboard_widget patches. Never ask which dashboard — use objectPath.\n"
            );
            case "mimic" -> sb.append(
                    "Guidance: SCADA mimic editor is open for objectPath. detail.elementCount/connectionCount describe "
                            + "the live diagram. Help list_mimic_symbols and save_mimic_diagram / add_mimic_elements. "
                            + "Never mention Ask/Plan/Execute — apply when the user asks to fill the mimic.\n"
            );
            case "workflow" -> sb.append(
                    "Guidance: help with BPMN workflows, forms, and process automation.\n"
            );
            case "report" -> sb.append(
                    "Guidance: help with report definitions, queries, and parameters.\n"
            );
            case "alert" -> sb.append(
                    "Guidance: help with alert / alarm configuration for this object.\n"
            );
            case "system" -> sb.append(
                    "Guidance: System console is open. detail.systemTab / settingsTab / screenTitle / "
                            + "visibleSettingIds describe THIS screen. Explain what the admin can do here "
                            + "(Refresh/Save, toggles, sections). Never ask which screen they mean.\n"
            );
            case "ai-studio" -> sb.append(
                    "Guidance: AI Studio (build workspace) is open — separate from Admin Copilot. "
                            + "detail.studioTab is agent|bundle|status|prefs. Explain that Studio tab "
                            + "and how it differs from Copilot screen help. Never ask which screen.\n"
            );
            default -> {
            }
        }
        return sb.toString();
    }

    /**
     * Short mid-conversation system note so chat history clarify-loops do not override live focus.
     */
    public static String formatLiveSnapshotReminder(String clientChannel, Map<String, Object> clientFocus) {
        if (!"copilot".equalsIgnoreCase(stringVal(clientChannel))) {
            return "";
        }
        if (clientFocus == null || clientFocus.isEmpty()) {
            return "";
        }
        String surface = stringVal(clientFocus.get("surface"));
        if (surface.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("LIVE UI SNAPSHOT (here-and-now — no prior chat turns; answer only from this):\n");
        sb.append("surface=").append(surface);
        String objectPath = stringVal(clientFocus.get("objectPath"));
        if (!objectPath.isBlank()) {
            sb.append(" objectPath=").append(objectPath);
        }
        sb.append('\n');
        if (!objectPath.isBlank()) {
            sb.append("SELECTED OBJECT (do not ask for path): ").append(objectPath).append('\n');
        }
        Object detail = clientFocus.get("detail");
        if (detail instanceof Map<?, ?> detailMap) {
            for (String key : new String[]{
                    "screenTitle", "systemTab", "settingsTab", "settingsTabLabel",
                    "studioTab", "screenHint", "inspectorTab"
            }) {
                String value = stringVal(detailMap.get(key));
                if (!value.isBlank()) {
                    sb.append(key).append("=").append(value).append('\n');
                }
            }
            Object visibleSettingIds = detailMap.get("visibleSettingIds");
            if (visibleSettingIds instanceof List<?> ids && !ids.isEmpty()) {
                sb.append("visibleSettingIds=");
                int limit = Math.min(ids.size(), 16);
                for (int i = 0; i < limit; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(stringVal(ids.get(i)));
                }
                sb.append('\n');
            }
            Object availableSystemTabs = detailMap.get("availableSystemTabs");
            if (availableSystemTabs instanceof List<?> tabs && !tabs.isEmpty()) {
                sb.append("availableSystemTabs=").append(tabs).append('\n');
            }
            Object availableStudioTabs = detailMap.get("availableStudioTabs");
            if (availableStudioTabs instanceof List<?> tabs && !tabs.isEmpty()) {
                sb.append("availableStudioTabs=").append(tabs).append('\n');
            }
            String ruleId = stringVal(detailMap.get("ruleId"));
            if (ruleId.isBlank()) {
                ruleId = extractTrailRuleId(detailMap.get("trail"));
            }
            if (!ruleId.isBlank()) {
                sb.append("ruleId=").append(ruleId).append('\n');
            }
            String expr = stringVal(detailMap.get("expression"));
            if (!expr.isBlank()) {
                if (expr.length() > 500) {
                    expr = expr.substring(0, 500) + "…";
                }
                sb.append("EXPRESSION:\n").append(expr).append('\n');
            }
            appendRulesList(sb, detailMap.get("rules"), "");
            Object trail = detailMap.get("trail");
            if (trail instanceof List<?> trailList && !trailList.isEmpty()) {
                sb.append("trail=");
                boolean first = true;
                for (Object step : trailList) {
                    if (!(step instanceof Map<?, ?> stepMap)) {
                        continue;
                    }
                    String label = stringVal(stepMap.get("label"));
                    if (label.isBlank()) {
                        label = stringVal(stepMap.get("surface"));
                    }
                    if (label.isBlank()) {
                        continue;
                    }
                    if (!first) {
                        sb.append(" › ");
                    }
                    sb.append(label);
                    first = false;
                }
                sb.append('\n');
            }
        }
        sb.append("FORBIDDEN: asking which object/rule/expression/screen when the fields above are present.");
        return sb.toString();
    }

    /**
     * Compact prefix for the LLM user turn (not shown in chat UI) so focus survives long ASK prompts.
     */
    public static String formatUserTurnPrefix(String clientChannel, Map<String, Object> clientFocus) {
        if (!"copilot".equalsIgnoreCase(stringVal(clientChannel))) {
            return "";
        }
        if (clientFocus == null || clientFocus.isEmpty()) {
            return "[UI CONTEXT] Admin Copilot — no specific screen focus was sent; ask only if still unclear.";
        }
        String surface = stringVal(clientFocus.get("surface"));
        if (surface.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[UI CONTEXT — answer about THIS screen / rule / expression; do not ask which]\n");
        sb.append("surface: ").append(surface).append('\n');
        String objectPath = stringVal(clientFocus.get("objectPath"));
        if (!objectPath.isBlank()) {
            sb.append("objectPath: ").append(objectPath).append('\n');
        }
        Object detail = clientFocus.get("detail");
        if (detail instanceof Map<?, ?> detailMap) {
            Object trail = detailMap.get("trail");
            if (trail instanceof List<?> trailList && !trailList.isEmpty()) {
                sb.append("trail:\n");
                int i = 0;
                for (Object step : trailList) {
                    if (!(step instanceof Map<?, ?> stepMap)) {
                        continue;
                    }
                    i++;
                    sb.append("  ").append(i).append(". ")
                            .append(stringVal(stepMap.get("label")));
                    String stepSurface = stringVal(stepMap.get("surface"));
                    if (!stepSurface.isBlank()) {
                        sb.append(" [").append(stepSurface).append(']');
                    }
                    sb.append('\n');
                }
            }
            String ruleId = stringVal(detailMap.get("ruleId"));
            if (ruleId.isBlank()) {
                ruleId = extractTrailRuleId(detailMap.get("trail"));
            }
            appendDetailLine(sb, "ruleId", ruleId);
            appendDetailLine(sb, "inspectorTab", detailMap.get("inspectorTab"));
            appendDetailLine(sb, "editorTitle", detailMap.get("editorTitle"));
            appendDetailLine(sb, "screenTitle", detailMap.get("screenTitle"));
            appendDetailLine(sb, "systemTab", detailMap.get("systemTab"));
            appendDetailLine(sb, "settingsTab", detailMap.get("settingsTab"));
            appendDetailLine(sb, "studioTab", detailMap.get("studioTab"));
            appendDetailLine(sb, "screenHint", detailMap.get("screenHint"));
            Object expression = detailMap.get("expression");
            if (expression != null && !stringVal(expression).isBlank()) {
                String expr = stringVal(expression);
                if (expr.length() > 500) {
                    expr = expr.substring(0, 500) + "…";
                }
                sb.append("EXPRESSION:\n").append(expr).append('\n');
            }
            Object target = detailMap.get("target");
            if (target != null && !stringVal(target).isBlank()) {
                sb.append("target: ").append(stringVal(target)).append('\n');
            }
            appendRulesList(sb, detailMap.get("rules"), "");
        }
        if (!objectPath.isBlank()) {
            sb.append("SELECTED OBJECT: ").append(objectPath)
                    .append(" — answer about this path; do not ask for it.\n");
        }
        sb.append("User question follows:");
        return sb.toString();
    }

    /** Compact map suitable for logging / tests (drops blank fields). */
    public static Map<String, Object> sanitize(Map<String, Object> clientFocus) {
        if (clientFocus == null || clientFocus.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : new String[]{"surface", "objectPath", "objectType", "editorTabId"}) {
            String value = stringVal(clientFocus.get(key));
            if (!value.isBlank()) {
                out.put(key, value);
            }
        }
        Object detail = clientFocus.get("detail");
        if (detail instanceof Map<?, ?> detailMap && !detailMap.isEmpty()) {
            Map<String, Object> cleanDetail = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : detailMap.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                cleanDetail.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            if (!cleanDetail.isEmpty()) {
                out.put("detail", cleanDetail);
            }
        }
        return out;
    }

    private static String extractTrailRuleId(Object trailRaw) {
        if (!(trailRaw instanceof List<?> trailList)) {
            return "";
        }
        for (int i = trailList.size() - 1; i >= 0; i--) {
            Object step = trailList.get(i);
            if (!(step instanceof Map<?, ?> stepMap)) {
                continue;
            }
            if (!"binding-rule".equalsIgnoreCase(stringVal(stepMap.get("surface")))) {
                continue;
            }
            String label = stringVal(stepMap.get("label"));
            if (label.startsWith("rule:")) {
                return label.substring("rule:".length()).trim();
            }
            Object detail = stepMap.get("detail");
            if (detail instanceof Map<?, ?> detailMap) {
                String ruleId = stringVal(detailMap.get("ruleId"));
                if (!ruleId.isBlank()) {
                    return ruleId;
                }
            }
        }
        return "";
    }

    private static void appendRulesList(StringBuilder sb, Object rulesRaw, String indent) {
        if (!(rulesRaw instanceof List<?> ruleList) || ruleList.isEmpty()) {
            return;
        }
        String prefix = indent == null ? "" : indent;
        sb.append(prefix).append("rules on this object (").append(ruleList.size()).append("):\n");
        int limit = Math.min(ruleList.size(), 12);
        for (int i = 0; i < limit; i++) {
            Object rule = ruleList.get(i);
            if (!(rule instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            sb.append(prefix)
                    .append("  - ")
                    .append(stringVal(ruleMap.get("id")))
                    .append(" → ")
                    .append(stringVal(ruleMap.get("target")))
                    .append(" :: ")
                    .append(stringVal(ruleMap.get("expression")))
                    .append('\n');
        }
    }

    private static void appendTrail(StringBuilder sb, Object trailRaw) {
        if (!(trailRaw instanceof List<?> trailList) || trailList.isEmpty()) {
            return;
        }
        sb.append("- navigation trail (oldest → newest):\n");
        int i = 0;
        for (Object step : trailList) {
            if (!(step instanceof Map<?, ?> stepMap)) {
                continue;
            }
            i++;
            sb.append("  ").append(i).append(". ")
                    .append(stringVal(stepMap.get("surface")));
            String label = stringVal(stepMap.get("label"));
            if (!label.isBlank()) {
                sb.append(" — ").append(label);
            }
            String path = stringVal(stepMap.get("objectPath"));
            if (!path.isBlank()) {
                sb.append(" @ ").append(path);
            }
            sb.append('\n');
        }
    }

    private static void appendRecentActions(StringBuilder sb, Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        sb.append("- recent UI actions:\n");
        int start = Math.max(0, list.size() - 10);
        for (int i = start; i < list.size(); i++) {
            Object step = list.get(i);
            if (!(step instanceof Map<?, ?> stepMap)) {
                continue;
            }
            sb.append("  - ").append(stringVal(stepMap.get("label")));
            String at = stringVal(stepMap.get("at"));
            if (!at.isBlank()) {
                sb.append(" (").append(at).append(')');
            }
            sb.append('\n');
        }
    }

    private static void appendDetailLine(StringBuilder sb, String key, Object raw) {
        String value = stringVal(raw);
        if (!value.isBlank()) {
            sb.append(key).append(": ").append(value).append('\n');
        }
    }

    private static void appendIfPresent(StringBuilder sb, String key, Object raw) {
        String value = stringVal(raw);
        if (!value.isBlank()) {
            sb.append("- ").append(key).append(": ").append(value).append('\n');
        }
    }

    private static String stringVal(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
