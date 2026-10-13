package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class AgentLoopGuard {

    private static final int RECENT_WINDOW = 5;
    private static final int REPEAT_THRESHOLD = 3;
    /** After this many prior calls of the same discovery tool, the next call is hard-blocked. */
    private static final int DISCOVERY_HARD_BLOCK = 2;
    /**
     * Per-dashboard/mimic path: after this many {@code add_dashboard_widget} / {@code add_mimic_elements}
     * calls, hard-block and force a batch layout/diagram tool.
     */
    private static final int MUTATE_SPAM_HARD_BLOCK = 4;

    private static final Set<String> DISCOVERY_LOOP_TOOLS = Set.of(
            "get_driver_help",
            "search_context",
            "list_drivers",
            "get_automation_schema",
            "search_platform_recipes",
            "list_examples",
            "get_example_bundle",
            "get_widget_catalog",
            "get_deploy_playbook"
    );

    private static final Set<String> MUTATE_SPAM_TOOLS = Set.of(
            "add_dashboard_widget",
            "add_mimic_elements"
    );

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
                    + " (exact folder — listing parent=root does NOT ground root.platform.* paths). "
                    + "Verify objects[].path in the result, then retry create_object.";
        }
        if ("save_workflow_bpmn".equals(lastTool) && lastStepErrorContains(steps, "not discovered in this turn")) {
            return """
                    save_workflow_bpmn blocked: WORKFLOW object must exist first. \
                    Order: list_objects parent=root.platform.workflows → create_object type=WORKFLOW → \
                    save_workflow_bpmn path=<path from create_object result>.""";
        }
        if ("save_workflow_bpmn".equals(lastTool) && lastStepErrorContains(steps, "not found")) {
            return """
                    WORKFLOW object not found. create_object type=WORKFLOW must succeed before save_workflow_bpmn. \
                    Use the exact path returned by create_object (e.g. root.platform.workflows.hydraulic-shock).""";
        }
        if ("list_objects".equals(lastTool) && lastListedParentIsRoot(steps)) {
            return """
                    Listed root — for objects under root.platform.* call list_objects on the exact parent folder \
                    (e.g. parent=root.platform.workflows), not parent=root.""";
        }
        if ("run_report".equals(lastTool)) {
            return """
                    Report data received. Prefer finish with a summary if the goal is met; \
                    otherwise continue with one more targeted read tool.""";
        }
        if ("get_driver_help".equals(lastTool) || "list_drivers".equals(lastTool)) {
            return snmpOrDriverExecuteHint();
        }
        if ("create_object".equals(lastTool)) {
            String typeHint = createObjectTypeHint(steps);
            if (typeHint != null) {
                return typeHint;
            }
            return """
                    Object created. For virtual simulators use create_virtual_device next time. \
                    If driverId=virtual: set templateId from list_mixin_blueprints OR recreate with create_virtual_device; \
                    set driverConfigJson from list_virtual_profiles defaults, configure_driver autoStart=true, list_variables before finish.""";
        }
        if ("save_workflow_bpmn".equals(lastTool) && lastStepGroundTruthBlocked(steps)) {
            return AgentGroundTruthGuard.treeFirstOrderHint(
                    "root.platform.workflows",
                    blockedObjectPath(steps),
                    "save_workflow_bpmn"
            );
        }
        if ("save_mimic_diagram".equals(lastTool) && lastStepGroundTruthBlocked(steps)) {
            return AgentGroundTruthGuard.treeFirstOrderHint(
                    "root.platform.mimics",
                    blockedObjectPath(steps),
                    "save_mimic_diagram"
            );
        }
        if ("set_dashboard_layout".equals(lastTool) && lastStepGroundTruthBlocked(steps)) {
            return AgentGroundTruthGuard.treeFirstOrderHint(
                    "root.platform.dashboards",
                    blockedObjectPath(steps),
                    "set_dashboard_layout"
            );
        }
        if (lastStepGroundTruthBlocked(steps)) {
            String objectPath = blockedObjectPath(steps);
            String parent = objectPath.contains(".") ? objectPath.substring(0, objectPath.lastIndexOf('.')) : objectPath;
            return AgentGroundTruthGuard.treeFirstOrderHint(parent, objectPath, lastTool);
        }
        if ("search_platform_recipes".equals(lastTool) || "get_automation_schema".equals(lastTool)) {
            return """
                    Recipe/schema loaded — this is a pattern, not live tree state. \
                    Next: list_objects on the real parent folder, list_mixin_blueprints / list_virtual_profiles, \
                    then create using paths and modelName from those tool results only. \
                    For SNMP monitoring: stop more get_driver_help — enable_agent_tool_pack devices+dashboards, \
                    then create_object DEVICE templateId=snmp-agent-v1 driverId=snmp.""";
        }
        if ("create_virtual_device".equals(lastTool)) {
            return """
                    Virtual device provisioned. Verify telemetryVariableCount>0; bind SCADA/dashboard using returned variable names; \
                    list_variables on each device before finish.""";
        }
        if ("get_example_bundle".equals(lastTool)) {
            return """
                    Example bundle loaded — full manifest is in the tool result above and in the UI step. \
                    Finish NOW with {"type":"finish","summary":"Markdown: section names and purpose only","result":{}}. \
                    Do NOT embed manifest JSON in summary (no ``` fences) or in result.plan.sections. \
                    Re-calling get_example_bundle is unnecessary unless the user asks for another appId/sections.""";
        }
        if ("add_dashboard_widget".equals(lastTool)) {
            long adds = totalToolCount(steps, "add_dashboard_widget");
            if (adds >= 2) {
                return """
                        Stop add_dashboard_widget loops. Prefer ONE set_dashboard_layout path=<dashboard> \
                        template= or layoutJson with all widgets (columns=84). Then finish. \
                        Do NOT call add_dashboard_widget again for every KPI/chart.""";
            }
        }
        if ("add_mimic_elements".equals(lastTool)) {
            long adds = totalToolCount(steps, "add_mimic_elements");
            if (adds >= 2) {
                return """
                        Stop add_mimic_elements loops. Prefer ONE save_mimic_diagram path=<mimic> \
                        with the full elements[] array, then get_mimic_diagram to verify, then finish.""";
            }
        }
        if (isRepeatedTool(lastTool, steps)) {
            if (DISCOVERY_LOOP_TOOLS.contains(normalize(lastTool))) {
                return snmpOrDriverExecuteHint();
            }
            return """
                    You called the same tool repeatedly. Change strategy or emit {"type":"finish",...}. \
                    If the user's intent is unclear, prefer finish with a short question and result.suggestions \
                    (label + message per option, interactive=true) instead of more blind tool calls. \
                    For dashboards: list_variables first; use set_dashboard_layout template= or one presentable \
                    layoutJson (columns=84, KPI w=21|28 h=14, charts ≥42×28) instead of many add_dashboard_widget; \
                    never set_variable name=widgets. get_widget_catalog type=<type> for exact widget fields. \
                    For SCADA mimics: save_mimic_diagram or add_mimic_elements with non-empty elements[]; \
                    list_mimic_symbols for symbolId; get_mimic_diagram to verify elementCount; never set_variable name=diagram. \
                    For platform docs: use prior search_context / get_driver_help results — do NOT re-call them. \
                    Before invoke_bff: use list_functions and get_function instead of guessing function names.""";
        }
        long recentSearch = recentToolCount(steps, "search_context");
        if (recentSearch >= REPEAT_THRESHOLD) {
            return """
                    Stop search_context loops. Use concrete tree tools: enable_agent_tool_pack, create_object, \
                    set_variable, configure_driver, set_dashboard_layout. \
                    Finish with {"type":"finish",...} when done.""";
        }
        return defaultHint(steps, maxStepsTotal);
    }

    private static String snmpOrDriverExecuteHint() {
        return """
                Driver/docs discovery is enough — STOP get_driver_help / search_context loops. \
                Execute SNMP demo NOW: enable_agent_tool_pack pack=devices then pack=dashboards; \
                list_objects parent=root.platform.devices; \
                create_object parentPath=root.platform.devices name=demo-snmp type=DEVICE \
                displayName="Demo SNMP" templateId=snmp-agent-v1 driverId=snmp autoStartDriver=false; \
                set_variable path=<device> name=driverConfigJson value={"host":"127.0.0.1","port":161,"version":"2c","community":"public"}; \
                configure_driver devicePath=<device> driverId=snmp autoStart=true; list_variables path=<device>; \
                create_object DASHBOARD under root.platform.dashboards; \
                set_dashboard_layout template=snmp-host-monitoring (or empty + widgets bound to list_variables).""";
    }

    private static String defaultHint(List<Map<String, Object>> steps, int maxStepsTotal) {
        int stepCount = steps != null ? steps.size() : 0;
        int remainingTotal = Math.max(0, maxStepsTotal - stepCount);
        String pace = AgentTurnPaceHints.gentlePaceSuffix(steps, maxStepsTotal);
        if (remainingTotal <= 8) {
            return "Step limit almost reached (" + stepCount + "/" + maxStepsTotal
                    + ") — finish now with {\"type\":\"finish\",\"summary\":\"...\",\"result\":{...}}."
                    + pace;
        }
        String base = "Continue with another tool action or finish when the goal is complete.";
        return pace.isEmpty() ? base : base + pace;
    }

    static Optional<BlockDecision> checkHardBlock(String toolName, List<Map<String, Object>> steps) {
        return checkHardBlock(toolName, Map.of(), steps);
    }

    static Optional<BlockDecision> checkHardBlock(
            String toolName,
            Map<String, Object> toolArgs,
            List<Map<String, Object>> steps
    ) {
        if (repeatGroundTruthError(toolName, steps) >= 2) {
            String parent = blockedParentPath(steps);
            return Optional.of(new BlockDecision(
                    "Hard block: same ground-truth error repeated for " + toolName,
                    "Mandatory: list_objects parent=" + parent
                            + " — use exact paths from result, then retry. Problem Brief: do not replan — fix grounding."
            ));
        }
        String normalized = normalize(toolName);
        if (DISCOVERY_LOOP_TOOLS.contains(normalized)
                && totalToolCount(steps, normalized) >= DISCOVERY_HARD_BLOCK) {
            return Optional.of(new BlockDecision(
                    "Hard block: discovery tool '" + normalized + "' called "
                            + totalToolCount(steps, normalized)
                            + " times this turn — stop looping",
                    snmpOrDriverExecuteHint()
            ));
        }
        if (MUTATE_SPAM_TOOLS.contains(normalized)) {
            String pathKey = mutateTargetPath(normalized, toolArgs);
            long prior = totalToolCountOnPath(steps, normalized, pathKey);
            if (prior >= MUTATE_SPAM_HARD_BLOCK) {
                String hint = "add_dashboard_widget".equals(normalized)
                        ? """
                        Hard block: too many add_dashboard_widget on the same dashboard. \
                        Call set_dashboard_layout path=<dashboard> template= or one layoutJson with all widgets, \
                        then finish. Do not add widgets one-by-one."""
                        : """
                        Hard block: too many add_mimic_elements on the same mimic. \
                        Call save_mimic_diagram path=<mimic> with full elements[], then finish.""";
                return Optional.of(new BlockDecision(
                        "Hard block: '" + normalized + "' called " + prior
                                + " times this turn"
                                + (pathKey.isBlank() ? "" : " on " + pathKey)
                                + " — stop looping",
                        hint
                ));
            }
        }
        return Optional.empty();
    }

    static String mutateTargetPath(String toolName, Map<String, Object> toolArgs) {
        Map<String, Object> args = toolArgs == null ? Map.of() : toolArgs;
        String path = stringArg(args, "path");
        if (path.isBlank()) {
            path = stringArg(args, "dashboardPath");
        }
        if (path.isBlank()) {
            path = stringArg(args, "mimicPath");
        }
        return path.toLowerCase(Locale.ROOT);
    }

    private static long totalToolCountOnPath(
            List<Map<String, Object>> steps,
            String toolName,
            String pathKey
    ) {
        if (steps == null || toolName == null) {
            return 0;
        }
        String normalized = normalize(toolName);
        String want = pathKey == null ? "" : pathKey;
        return steps.stream()
                .filter(step -> "tool".equals(String.valueOf(step.get("type"))))
                .filter(step -> normalized.equals(normalize(String.valueOf(step.get("tool")))))
                .filter(step -> {
                    String stepPath = mutateTargetPath(normalized, stepMap(step, "arguments"));
                    if (want.isBlank() && stepPath.isBlank()) {
                        return true;
                    }
                    return !want.isBlank() && want.equals(stepPath);
                })
                .count();
    }

    record BlockDecision(String error, String hint) {
        boolean blocked() {
            return error != null && !error.isBlank();
        }
    }

    private static int repeatGroundTruthError(String toolName, List<Map<String, Object>> steps) {
        if (steps == null || toolName == null) {
            return 0;
        }
        int count = 0;
        int start = Math.max(0, steps.size() - 4);
        for (int i = start; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            if (!toolName.equalsIgnoreCase(String.valueOf(step.get("tool")))) {
                continue;
            }
            if (lastStepGroundTruthBlocked(List.of(step))) {
                count++;
            }
        }
        return count;
    }

    private static boolean isRepeatedTool(String lastTool, List<Map<String, Object>> steps) {
        if (lastTool == null || lastTool.isBlank()) {
            return false;
        }
        return recentToolCount(steps, lastTool) >= REPEAT_THRESHOLD;
    }

    private static long recentToolCount(List<Map<String, Object>> steps, String toolName) {
        if (steps == null || toolName == null) {
            return 0;
        }
        String normalized = normalize(toolName);
        int start = Math.max(0, steps.size() - RECENT_WINDOW);
        return steps.stream()
                .skip(start)
                .filter(step -> "tool".equals(String.valueOf(step.get("type"))))
                .map(step -> step.get("tool"))
                .filter(Objects::nonNull)
                .map(tool -> normalize(String.valueOf(tool)))
                .filter(tool -> tool.equals(normalized))
                .count();
    }

    private static long totalToolCount(List<Map<String, Object>> steps, String toolName) {
        if (steps == null || toolName == null) {
            return 0;
        }
        String normalized = normalize(toolName);
        return steps.stream()
                .filter(step -> "tool".equals(String.valueOf(step.get("type"))))
                .map(step -> step.get("tool"))
                .filter(Objects::nonNull)
                .map(tool -> normalize(String.valueOf(tool)))
                .filter(tool -> tool.equals(normalized))
                .count();
    }

    private static String normalize(String toolName) {
        return toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
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

    private static String blockedObjectPath(List<Map<String, Object>> steps) {
        Map<String, Object> last = steps.get(steps.size() - 1);
        String error = String.valueOf(stepMap(last, "result").get("error"));
        int marker = error.lastIndexOf(": ");
        if (marker >= 0 && marker + 2 < error.length()) {
            return error.substring(marker + 2).trim();
        }
        return "";
    }

    private static String createObjectTypeHint(List<Map<String, Object>> steps) {
        if (steps.isEmpty()) {
            return null;
        }
        Map<String, Object> last = steps.get(steps.size() - 1);
        if (!"create_object".equals(String.valueOf(last.get("tool")))) {
            return null;
        }
        if (!"OK".equals(String.valueOf(stepMap(last, "result").get("status")))) {
            return null;
        }
        Map<String, Object> args = stepMap(last, "arguments");
        String type = stringArg(args, "type").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "WORKFLOW" -> """
                    WORKFLOW created. Next: save_workflow_bpmn path=<path from create_object result>, \
                    then update_workflow_status status=ACTIVE, then run_workflow.""";
            case "MIMIC" -> """
                    MIMIC created. Next: list_mimic_symbols, then save_mimic_diagram path=<path from create_object result> \
                    with non-empty elements[].""";
            case "DASHBOARD" -> """
                    DASHBOARD created. Next: list_variables on source devices, then set_dashboard_layout path=<path from create_object result> \
                    template= or add_dashboard_widget.""";
            case "DEVICE" -> """
                    DEVICE created. Next: configure_driver, list_variables — verify telemetry before dashboard/SCADA bindings.""";
            case "ALERT" -> "ALERT created. Next: configure_alert path=<path from create_object result>.";
            case "REPORT" -> "REPORT created. Next: configure_report path=<path from create_object result>.";
            default -> null;
        };
    }

    private static String blockedParentPath(List<Map<String, Object>> steps) {
        Map<String, Object> last = steps.get(steps.size() - 1);
        String error = String.valueOf(stepMap(last, "result").get("error"));
        int marker = error.lastIndexOf(": ");
        if (marker >= 0 && marker + 2 < error.length()) {
            return error.substring(marker + 2).trim();
        }
        return "root.platform.workflows";
    }

    private static boolean lastStepErrorContains(List<Map<String, Object>> steps, String fragment) {
        if (steps.isEmpty()) {
            return false;
        }
        Map<String, Object> last = steps.get(steps.size() - 1);
        if (!"tool".equals(String.valueOf(last.get("type")))) {
            return false;
        }
        String error = String.valueOf(stepMap(last, "result").get("error"));
        return error.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private static boolean lastListedParentIsRoot(List<Map<String, Object>> steps) {
        if (steps.isEmpty()) {
            return false;
        }
        Map<String, Object> last = steps.get(steps.size() - 1);
        if (!"tool".equals(String.valueOf(last.get("type")))) {
            return false;
        }
        if (!"list_objects".equals(String.valueOf(last.get("tool")))) {
            return false;
        }
        Map<String, Object> args = stepMap(last, "arguments");
        String parent = stringArg(args, "parent");
        if (parent.isBlank()) {
            parent = stringArg(args, "parentPath");
        }
        if (parent.isBlank()) {
            parent = "root";
        }
        return "root".equalsIgnoreCase(parent.trim())
                && "OK".equals(String.valueOf(stepMap(last, "result").get("status")));
    }

    private static String stringArg(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return "";
        }
        Object value = arguments.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
