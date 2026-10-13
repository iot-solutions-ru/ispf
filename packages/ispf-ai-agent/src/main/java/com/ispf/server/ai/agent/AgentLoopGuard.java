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
     * Incremental UI builders (widget/element adds): after this many prior same-target calls,
     * soft-block with a batch-tool hint (turn continues so the model can switch strategy).
     */
    private static final int BATCH_INCREMENTAL_HARD_BLOCK = 2;
    /**
     * After this many prior same-target incremental adds, stop the turn — the Studio log is flooded.
     */
    private static final int BATCH_INCREMENTAL_STOP_TURN = 10;
    /** Same read/list fingerprint (tool + target): hard-block and stop the turn. */
    private static final int READ_SPAM_HARD_BLOCK = 3;
    /** Safety net: any tool with the same fingerprint this many times → stop (prevents UI log floods). */
    private static final int IDENTICAL_CALL_HARD_BLOCK = 5;
    /** Last N tool names inspected for A↔B oscillation (e.g. list_variables ↔ get_object). */
    private static final int OSCILLATION_WINDOW = 8;
    /** Minimum alternating steps (proposed call included) before hard-block — 3 full A/B cycles. */
    private static final int OSCILLATION_MIN_STEPS = 6;

    private static final Set<String> DISCOVERY_LOOP_TOOLS = Set.of(
            "get_driver_help",
            "search_context",
            "list_drivers",
            "get_automation_schema",
            "search_platform_recipes",
            "list_examples",
            "get_example_bundle",
            "get_deploy_playbook"
    );

    /**
     * Catalog lookups keyed by {@code type} (fingerprint). Different widget types must be allowed
     * in one course turn; only repeating the same type is spam.
     */
    private static final Set<String> TYPED_CATALOG_TOOLS = Set.of(
            "get_widget_catalog"
    );

    /** After this many prior identical create_object OK/reused, stop — do not flood the step log. */
    private static final int CREATE_REUSE_HARD_BLOCK = 2;
    /**
     * Truly idempotent mutates with no progressive content: after this many prior identical OK calls
     * on the same fingerprint, stop.
     */
    private static final int IDEMPOTENT_OK_HARD_BLOCK = 2;

    private static final Set<String> IDEMPOTENT_MUTATE_TOOLS = Set.of(
            "configure_report"
    );

    /**
     * Consecutive trailing OK set_dashboard_layout on the same path — stop spam loops (×25).
     * Intervening add_dashboard_widget / get_widget_catalog resets the streak so course re-applies are OK.
     */
    private static final int LAYOUT_CONSECUTIVE_OK_HARD_BLOCK = 2;

    /** Prefer one batch write over many incremental adds — noisy in Studio step log. */
    private static final Set<String> BATCH_INCREMENTAL_TOOLS = Set.of(
            "add_dashboard_widget",
            "add_mimic_elements"
    );

    private static final Set<String> READ_SPAM_TOOLS = Set.of(
            "list_objects",
            "get_object",
            "list_variables",
            "describe_variables",
            "get_dashboard_layout",
            "get_mimic_diagram",
            "list_mimic_symbols",
            "list_functions",
            "get_function"
    );

    /**
     * Tools that participate in read/discovery ping-pong. Mutate pairs
     * (e.g. create_object ↔ set_dashboard_layout for many course dashboards) are not noise.
     */
    private static final Set<String> OSCILLATION_READ_TOOLS = Set.of(
            "list_objects",
            "get_object",
            "list_variables",
            "describe_variables",
            "get_dashboard_layout",
            "get_mimic_diagram",
            "list_mimic_symbols",
            "list_functions",
            "get_function",
            "get_widget_catalog",
            "search_context",
            "get_automation_schema",
            "list_drivers",
            "get_driver_help",
            "list_instance_types",
            "list_mixin_blueprints",
            "get_object_blueprint"
    );

    /** Args that identify the target of a call (body payloads intentionally excluded). */
    private static final List<String> FINGERPRINT_KEYS = List.of(
            "path",
            "parent",
            "parentPath",
            "dashboardPath",
            "mimicPath",
            "devicePath",
            "name",
            "query",
            "topic",
            "type",
            "pack",
            "templateId",
            "driverId"
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
        String objectExistsHint = objectAlreadyExistsHint(lastTool, steps);
        if (objectExistsHint != null) {
            return objectExistsHint;
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
        if ("set_variable".equals(lastTool) && lastStepErrorContains(steps, "Variable not found: layout")) {
            return """
                    Operator HMI layout is NOT a tree variable. Use configure_operator_ui \
                    appId=<short-id> title=... defaultDashboard=<dashboardPath> \
                    dashboards=[{path,title}]. Do NOT set_variable name=layout.""";
        }
        if (isReadToolOscillation(steps)) {
            return """
                    Stop read ping-pong (alternating list_*/get_*). Use prior tool results. \
                    Next: one mutate (configure_operator_ui / configure_alert / set_dashboard_layout) or finish.""";
        }
        String batchHint = batchIncrementalHint(lastTool, steps);
        if (batchHint != null) {
            return batchHint;
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
        Optional<BlockDecision> sameError = checkRepeatedIdenticalError(toolName, steps);
        if (sameError.isPresent()) {
            return sameError;
        }
        Optional<BlockDecision> oscillation = checkToolOscillation(toolName, steps);
        if (oscillation.isPresent()) {
            return oscillation;
        }
        String normalized = normalize(toolName);
        if (DISCOVERY_LOOP_TOOLS.contains(normalized)
                && totalToolCount(steps, normalized) >= DISCOVERY_HARD_BLOCK) {
            return Optional.of(new BlockDecision(
                    "Hard block: discovery tool '" + normalized + "' called "
                            + totalToolCount(steps, normalized)
                            + " times this turn — stop looping",
                    snmpOrDriverExecuteHint(),
                    false
            ));
        }

        String fingerprint = callFingerprint(normalized, toolArgs);
        long priorSame = totalFingerprintCount(steps, fingerprint);

        if (TYPED_CATALOG_TOOLS.contains(normalized) && priorSame >= DISCOVERY_HARD_BLOCK) {
            return Optional.of(new BlockDecision(
                    "Hard block: '" + normalized + "' repeated " + priorSame
                            + " times for the same type — stop looping",
                    "Reuse the prior catalog result for this widget type. "
                            + "Call get_widget_catalog only when the type argument changes, "
                            + "then set_dashboard_layout / add_dashboard_widget.",
                    false
            ));
        }

        if ("create_object".equals(normalized)
                && priorSame >= CREATE_REUSE_HARD_BLOCK
                && fingerprintHasReusedOk(steps, fingerprint)) {
            Map<String, Object> args = toolArgs == null ? Map.of() : toolArgs;
            String type = stringArg(args, "type").toUpperCase(Locale.ROOT);
            String path = resolveCreateObjectPath(args, steps, fingerprint);
            return Optional.of(new BlockDecision(
                    "Hard block: identical 'create_object' already reused " + priorSame
                            + " times — stop flooding the step log",
                    AgentObjectReuse.nextStepHint(path, type)
                            + " Do NOT call create_object again for this name.",
                    true
            ));
        }

        if (IDEMPOTENT_MUTATE_TOOLS.contains(normalized)
                && priorSame >= IDEMPOTENT_OK_HARD_BLOCK
                && fingerprintAllStatusOk(steps, fingerprint)) {
            return Optional.of(new BlockDecision(
                    "Hard block: identical '" + normalized + "' already succeeded " + priorSame
                            + " times — stop flooding the step log",
                    "Reuse the prior OK result. Continue the next course/assignment step "
                            + "(different dashboard/report/mimic path) or finish — do not re-apply the same layout.",
                    true
            ));
        }

        if (BATCH_INCREMENTAL_TOOLS.contains(normalized) && priorSame >= BATCH_INCREMENTAL_HARD_BLOCK) {
            boolean stopTurn = priorSame >= BATCH_INCREMENTAL_STOP_TURN;
            return Optional.of(new BlockDecision(
                    (stopTurn ? "Hard block: '" : "Batch hint: '") + normalized + "' repeated " + priorSame
                            + " times on the same target — "
                            + (stopTurn ? "stop flooding the step log" : "switch to a batch write"),
                    batchIncrementalHardHint(normalized),
                    stopTurn
            ));
        }
        if (READ_SPAM_TOOLS.contains(normalized) && priorSame >= READ_SPAM_HARD_BLOCK) {
            return Optional.of(new BlockDecision(
                    "Hard block: '" + normalized + "' repeated " + priorSame
                            + " times with the same arguments — stop looping",
                    "Use the prior tool result. Change path/parent/query or finish with a summary. "
                            + "Do not re-list the same folder.",
                    true
            ));
        }
        if ("set_dashboard_layout".equals(normalized)) {
            long consecutiveOk = consecutiveTrailingFingerprintOkCount(steps, fingerprint);
            if (consecutiveOk >= LAYOUT_CONSECUTIVE_OK_HARD_BLOCK) {
                return Optional.of(new BlockDecision(
                        "Hard block: consecutive 'set_dashboard_layout' succeeded " + consecutiveOk
                                + " times on the same dashboard — stop flooding the step log",
                        "Layout already applied. Prefer ONE set_dashboard_layout with the full widgets[] "
                                + "(after get_widget_catalog), or switch to another dashboard / finish. "
                                + "Do not re-call set_dashboard_layout in a tight loop.",
                        true
                ));
            }
            // Lifetime IDENTICAL_CALL would block legitimate re-applies after add_dashboard_widget.
            return Optional.empty();
        }
        if (priorSame >= IDENTICAL_CALL_HARD_BLOCK) {
            return Optional.of(new BlockDecision(
                    "Hard block: identical '" + normalized + "' call repeated " + priorSame
                            + " times this turn — stop flooding the step log",
                    "Change strategy (different tool/args) or finish. "
                            + "Batch writes (set_dashboard_layout / save_mimic_diagram / one create_object) "
                            + "beat dozens of tiny OK steps.",
                    true
            ));
        }
        return Optional.empty();
    }

    static String callFingerprint(String toolName, Map<String, Object> toolArgs) {
        String normalized = normalize(toolName);
        Map<String, Object> args = toolArgs == null ? Map.of() : toolArgs;
        StringBuilder sb = new StringBuilder(normalized);
        for (String key : FINGERPRINT_KEYS) {
            String value = stringArg(args, key);
            if (!value.isBlank()) {
                sb.append('|').append(key).append('=').append(value.toLowerCase(Locale.ROOT));
            }
        }
        // Batch-incremental: ignore widget/element body — same dashboard/mimic path is the spam signal.
        if (BATCH_INCREMENTAL_TOOLS.contains(normalized) && sb.indexOf("|path=") < 0) {
            String path = mutateTargetPath(args);
            if (!path.isBlank()) {
                sb.append("|path=").append(path);
            }
        }
        // Binding/alert: different expressions must not count as identical spam.
        if ("create_binding_rule".equals(normalized)) {
            appendFingerprintKey(sb, args, "id");
            appendFingerprintKey(sb, args, "targetVariable");
            appendFingerprintKey(sb, args, "expression");
        }
        if ("configure_alert".equals(normalized)) {
            appendFingerprintKey(sb, args, "conditionExpr");
            appendFingerprintKey(sb, args, "watchVariable");
            appendFingerprintKey(sb, args, "eventName");
        }
        return sb.toString();
    }

    private static void appendFingerprintKey(StringBuilder sb, Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        if (!value.isBlank()) {
            sb.append('|').append(key).append('=').append(value.toLowerCase(Locale.ROOT));
        }
    }

    static String mutateTargetPath(Map<String, Object> toolArgs) {
        Map<String, Object> args = toolArgs == null ? Map.of() : toolArgs;
        String path = stringArg(args, "path");
        if (path.isBlank()) {
            path = stringArg(args, "dashboardPath");
        }
        if (path.isBlank()) {
            path = stringArg(args, "mimicPath");
        }
        if (path.isBlank()) {
            path = stringArg(args, "devicePath");
        }
        return path.toLowerCase(Locale.ROOT);
    }

    private static long totalFingerprintCount(List<Map<String, Object>> steps, String fingerprint) {
        if (steps == null || fingerprint == null || fingerprint.isBlank()) {
            return 0;
        }
        return steps.stream()
                .filter(step -> "tool".equals(String.valueOf(step.get("type"))))
                .filter(step -> fingerprint.equals(callFingerprint(
                        String.valueOf(step.get("tool")),
                        stepMap(step, "arguments")
                )))
                .count();
    }

    /**
     * Count trailing tool steps that match fingerprint and returned OK — broken by any other tool/args.
     */
    private static long consecutiveTrailingFingerprintOkCount(
            List<Map<String, Object>> steps,
            String fingerprint
    ) {
        if (steps == null || fingerprint == null || fingerprint.isBlank()) {
            return 0;
        }
        long count = 0;
        for (int i = steps.size() - 1; i >= 0; i--) {
            Map<String, Object> step = steps.get(i);
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            String fp = callFingerprint(String.valueOf(step.get("tool")), stepMap(step, "arguments"));
            if (!fingerprint.equals(fp)) {
                break;
            }
            Map<String, Object> result = stepMap(step, "result");
            if (!"OK".equalsIgnoreCase(String.valueOf(result.get("status")))) {
                break;
            }
            count++;
        }
        return count;
    }

    private static boolean fingerprintAllStatusOk(List<Map<String, Object>> steps, String fingerprint) {
        if (steps == null || fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        boolean saw = false;
        for (Map<String, Object> step : steps) {
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            if (!fingerprint.equals(callFingerprint(String.valueOf(step.get("tool")), stepMap(step, "arguments")))) {
                continue;
            }
            saw = true;
            Map<String, Object> result = stepMap(step, "result");
            if (!"OK".equalsIgnoreCase(String.valueOf(result.get("status")))) {
                return false;
            }
        }
        return saw;
    }

    /** True when at least one prior identical create returned reused=true (object already exists). */
    private static boolean fingerprintHasReusedOk(List<Map<String, Object>> steps, String fingerprint) {
        if (steps == null || fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        for (Map<String, Object> step : steps) {
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            if (!fingerprint.equals(callFingerprint(String.valueOf(step.get("tool")), stepMap(step, "arguments")))) {
                continue;
            }
            Map<String, Object> result = stepMap(step, "result");
            boolean ok = "OK".equalsIgnoreCase(String.valueOf(result.get("status")));
            boolean reused = Boolean.TRUE.equals(result.get("reused"))
                    || "true".equalsIgnoreCase(String.valueOf(result.get("reused")));
            if (ok && reused) {
                return true;
            }
        }
        return false;
    }

    private static String resolveCreateObjectPath(
            Map<String, Object> args,
            List<Map<String, Object>> steps,
            String fingerprint
    ) {
        if (steps != null) {
            for (int i = steps.size() - 1; i >= 0; i--) {
                Map<String, Object> step = steps.get(i);
                if (!"tool".equals(String.valueOf(step.get("type")))) {
                    continue;
                }
                if (!fingerprint.equals(callFingerprint(String.valueOf(step.get("tool")), stepMap(step, "arguments")))) {
                    continue;
                }
                Map<String, Object> result = stepMap(step, "result");
                String path = firstNonBlank(stringArg(result, "path"), stringArg(result, "existingPath"));
                if (!path.isBlank()) {
                    return path;
                }
            }
        }
        String parent = stringArg(args, "parentPath");
        if (parent.isBlank()) {
            parent = stringArg(args, "parent");
        }
        String name = stringArg(args, "name");
        if (!parent.isBlank() && !name.isBlank()) {
            return parent + "." + name;
        }
        return "";
    }

    private static String batchIncrementalHint(String lastTool, List<Map<String, Object>> steps) {
        String normalized = normalize(lastTool);
        if (!BATCH_INCREMENTAL_TOOLS.contains(normalized)) {
            return null;
        }
        if (totalToolCount(steps, normalized) < 1) {
            return null;
        }
        return batchIncrementalHardHint(normalized);
    }

    private static String batchIncrementalHardHint(String normalized) {
        if ("add_dashboard_widget".equals(normalized)) {
            return """
                    Stop add_dashboard_widget noise. Prefer ONE set_dashboard_layout path=<dashboard> \
                    template= or layoutJson with all widgets (columns=84). Then finish. \
                    Do NOT call add_dashboard_widget for every KPI/chart.""";
        }
        return """
                Stop add_mimic_elements noise. Prefer ONE save_mimic_diagram path=<mimic> \
                with the full elements[] array, then get_mimic_diagram to verify, then finish.""";
    }

    /**
     * A↔B tool oscillation (e.g. list_variables ↔ get_object) floods the Studio log.
     * Blocks when the proposed call would continue a strict alternation of length ≥ {@link #OSCILLATION_MIN_STEPS}.
     */
    private static Optional<BlockDecision> checkToolOscillation(
            String toolName,
            List<Map<String, Object>> steps
    ) {
        List<String> recent = recentToolNames(steps, OSCILLATION_WINDOW - 1);
        if (recent.size() < OSCILLATION_MIN_STEPS - 1) {
            return Optional.empty();
        }
        List<String> sequence = new java.util.ArrayList<>(recent);
        sequence.add(normalize(toolName));
        if (sequence.size() > OSCILLATION_WINDOW) {
            sequence = sequence.subList(sequence.size() - OSCILLATION_WINDOW, sequence.size());
        }
        if (!isStrictTwoToolAlternation(sequence)) {
            return Optional.empty();
        }
        // Dashboard course pattern: layout ↔ add_widget is progressive, not a stuck ping-pong.
        if (sequence.contains("set_dashboard_layout")
                && (sequence.contains("add_dashboard_widget") || sequence.contains("get_widget_catalog"))) {
            return Optional.empty();
        }
        String a = sequence.get(0);
        String b = sequence.get(1);
        if (!isNoiseOscillationPair(a, b)) {
            // Productive course pattern: create_object DASHBOARD ↔ set_dashboard_layout (new path each cycle).
            return Optional.empty();
        }
        String existsPath = latestExistingObjectPath(steps);
        String hint;
        if (!existsPath.isBlank()
                && sequence.contains("create_object")
                && sequence.contains("get_object")) {
            hint = "Object(s) already exist (e.g. " + existsPath + "). "
                    + "Stop create_object↔get_object. "
                    + "Next: configure_alert / set_dashboard_layout / list_variables / configure_* on that path, "
                    + "or continue the next assignment step. Do NOT recreate.";
        } else {
            hint = """
                    Stop read ping-pong. Use prior results or finish. \
                    Prefer one mutate: configure_operator_ui / configure_alert / set_dashboard_layout / create_binding_rule.""";
        }
        return Optional.of(new BlockDecision(
                "Hard block: tools '" + a + "' ↔ '" + b
                        + "' oscillating " + sequence.size() + " steps — stop flooding the step log",
                hint,
                true
        ));
    }

    private static boolean isReadToolOscillation(List<Map<String, Object>> steps) {
        List<String> recent = recentToolNames(steps, OSCILLATION_WINDOW);
        if (recent.size() < OSCILLATION_MIN_STEPS || !isStrictTwoToolAlternation(recent)) {
            return false;
        }
        return isNoiseOscillationPair(recent.get(0), recent.get(1));
    }

    /**
     * True only for stuck loops: read↔read, or create_object↔get_object.
     * create_object↔set_dashboard_layout (multi-dashboard course builds) is productive.
     */
    private static boolean isNoiseOscillationPair(String a, String b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        if (("create_object".equals(a) && "get_object".equals(b))
                || ("get_object".equals(a) && "create_object".equals(b))) {
            return true;
        }
        return OSCILLATION_READ_TOOLS.contains(a) && OSCILLATION_READ_TOOLS.contains(b);
    }

    private static List<String> recentToolNames(List<Map<String, Object>> steps, int max) {
        if (steps == null || max <= 0) {
            return List.of();
        }
        List<String> names = new java.util.ArrayList<>();
        for (int i = steps.size() - 1; i >= 0 && names.size() < max; i--) {
            Map<String, Object> step = steps.get(i);
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            names.add(0, normalize(String.valueOf(step.get("tool"))));
        }
        return names;
    }

    private static boolean isStrictTwoToolAlternation(List<String> sequence) {
        if (sequence == null || sequence.size() < OSCILLATION_MIN_STEPS) {
            return false;
        }
        String a = sequence.get(0);
        String b = sequence.get(1);
        if (a == null || b == null || a.isBlank() || b.isBlank() || a.equals(b)) {
            return false;
        }
        for (int i = 0; i < sequence.size(); i++) {
            String expected = (i % 2 == 0) ? a : b;
            if (!expected.equals(sequence.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Same tool + same ERROR text ≥2 times → stop turn (missing-arg loops, etc.).
     */
    private static Optional<BlockDecision> checkRepeatedIdenticalError(
            String toolName,
            List<Map<String, Object>> steps
    ) {
        if (steps == null || toolName == null || steps.isEmpty()) {
            return Optional.empty();
        }
        String normalized = normalize(toolName);
        String lastError = null;
        int streak = 0;
        for (int i = steps.size() - 1; i >= 0; i--) {
            Map<String, Object> step = steps.get(i);
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            if (!normalized.equals(normalize(String.valueOf(step.get("tool"))))) {
                break;
            }
            Map<String, Object> result = stepMap(step, "result");
            if (!"ERROR".equals(String.valueOf(result.get("status")))) {
                break;
            }
            String error = String.valueOf(result.get("error"));
            if (lastError == null) {
                lastError = error;
                streak = 1;
            } else if (lastError.equals(error)) {
                streak++;
            } else {
                break;
            }
            if (streak >= 2) {
                String hint = identicalErrorHint(normalized, lastError);
                // Missing-arg / formal-verify loops: keep turn open so the model can retry with fixed args.
                // Exception: enable_agent_tool_pack without pack (common on vision turns) never self-corrects.
                boolean fixable = isMissingRequiredArgError(lastError) || isFormalVerificationError(lastError);
                boolean stopTurn = !fixable || "enable_agent_tool_pack".equals(normalized);
                return Optional.of(new BlockDecision(
                        "Hard block: '" + normalized + "' failed " + streak
                                + " times with the same error — stop flooding the step log",
                        hint,
                        stopTurn
                ));
            }
        }
        return Optional.empty();
    }

    private static boolean isMissingRequiredArgError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        String lower = error.toLowerCase(Locale.ROOT);
        return lower.contains("required")
                || lower.contains("are required")
                || lower.contains("is required")
                || lower.contains("missing")
                || lower.contains("must be ")
                || lower.contains("unknown_arg")
                || lower.contains("unknown role")
                || lower.contains("formal verification")
                || lower.contains("tautology")
                || lower.contains("unsatisfiable")
                || lower.contains("never discriminates")
                || lower.contains("invalid");
    }

    private static boolean isFormalVerificationError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        String lower = error.toLowerCase(Locale.ROOT);
        return lower.contains("formal verification")
                || lower.contains("tautology")
                || lower.contains("unsatisfiable")
                || lower.contains("never discriminates");
    }

    private static String identicalErrorHint(String toolName, String error) {
        String lower = error == null ? "" : error.toLowerCase(Locale.ROOT);
        if ("enable_agent_tool_pack".equals(toolName) || lower.contains("required_arg_missing at pack")) {
            return """
                    enable_agent_tool_pack REQUIRES arguments.pack. Example: \
                    {"type":"tool","name":"enable_agent_tool_pack","arguments":{"pack":"devices"}}. \
                    Valid packs: core, discovery, devices, dashboards, automation, bundles, scada, analytics, security, misc. \
                    If the user only pasted an image: finish with a short description of the image — \
                    do NOT call enable_agent_tool_pack with empty {}.""";
        }
        if ("get_event_schema".equals(toolName) || lower.contains("objectpath and eventname")) {
            return """
                    get_event_schema needs objectPath=<device path> and eventName=<name on that object>. \
                    Example: objectPath=root.platform.devices.lab-virtual-device eventName=Event1. \
                    First list_events path=<device> or get_object to see events[]. Do NOT use eventType.""";
        }
        if ("get_object_blueprint".equals(toolName) || lower.contains("blueprintname or blueprintid")) {
            return """
                    get_object_blueprint needs blueprintName or blueprintId (e.g. virtual-lab-v1). \
                    Call list_mixin_blueprints or list_object_blueprints first; pass the name from the result. \
                    Do NOT send empty id/name.""";
        }
        if ("create_binding_rule".equals(toolName) || lower.contains("path, id, expression")) {
            return """
                    create_binding_rule needs flat args: path, id, expression, targetVariable=<local var>. \
                    Example expression: read("root.platform.devices.virt-device-1/sineWave") > 0 \
                    && read(".../virt-device-2/sineWave") > 0 && read(".../virt-device-3/sineWave") > 0 \
                    ? "ERROR" : "OK". Do NOT send ruleJson. Call describe_agent_tool name=create_binding_rule once.""";
        }
        if ("create_variable".equals(toolName) && (lower.contains("must be double") || lower.contains("must be"))) {
            return """
                    create_variable initialValue must match valueType: for DOUBLE use 0 or 0.0 (number), \
                    or omit initialValue. Then create_binding_rule for sine+sawtooth sum. \
                    Do NOT send boolean/object/array as initialValue.""";
        }
        if ("create_variable".equals(toolName) || lower.contains("valuetype")) {
            return """
                    create_variable needs path, name, valueType (DOUBLE|BOOLEAN|STRING|INTEGER). \
                    Optional initialValue scalar (0 / 0.0 / \"\"). Do NOT use dataType. \
                    Computed waves sum: create_variable then create_binding_rule expression=read(\"self/sineWave\")+read(\"self/sawtoothWave\").""";
        }
        if ("configure_operator_ui".equals(toolName) || lower.contains("defaultdashboard")) {
            return """
                    configure_operator_ui needs title, defaultDashboard, dashboards=[{path,title}]. \
                    appId is a short id (virt-cluster), not a tree path. Do NOT create_object APPLICATION.""";
        }
        if ("set_variable".equals(toolName) && lower.contains("layout")) {
            return """
                    Do NOT set_variable name=layout on operator-apps. \
                    Use configure_operator_ui with title, defaultDashboard, dashboards=[].""";
        }
        if (lower.contains("unknown role")) {
            return """
                    USER roles must be lowercase platform ids: admin, developer, operator (or tenant-admin). \
                    Example: create_object type=USER name=user1 roles=operator password=ChangeMe!123. \
                    Do NOT send OPERATOR / ROLE_OPERATOR.""";
        }
        if ("configure_alert".equals(toolName) || isFormalVerificationError(error)) {
            return """
                    conditionExpr must discriminate — formal verify rejects tautologies (true, 1==1, true||…). \
                    Example: watchVariable=sineWave conditionExpr=self.sineWave[\"value\"] > 0 \
                    or watchVariable=clusterError conditionExpr=self.clusterError[\"value\"] == true. \
                    Retry configure_alert with a threshold/boolean compare on the watched variable.""";
        }
        return "Fix the error using the tool description / describe_agent_tool, then retry with corrected args. "
                + "Do not repeat the identical failing call. Prefer the next course/task step over clarify.";
    }

    /**
     * @param stopTurn when true, TreeFirstAgentService ends the turn after recording the block
     *                 (avoids ERROR spam in the Studio step log).
     */
    record BlockDecision(String error, String hint, boolean stopTurn) {
        BlockDecision(String error, String hint) {
            this(error, hint, false);
        }

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

    private static String objectAlreadyExistsHint(String lastTool, List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty() || lastTool == null) {
            return null;
        }
        String tool = normalize(lastTool);
        if (!"create_object".equals(tool) && !"create_virtual_device".equals(tool)) {
            return null;
        }
        Map<String, Object> last = steps.get(steps.size() - 1);
        if (!tool.equals(normalize(String.valueOf(last.get("tool"))))) {
            return null;
        }
        Map<String, Object> result = stepMap(last, "result");
        Map<String, Object> args = stepMap(last, "arguments");
        String type = "create_virtual_device".equals(tool)
                ? "DEVICE"
                : stringArg(args, "type").toUpperCase(Locale.ROOT);
        if (Boolean.TRUE.equals(result.get("reused"))
                || "true".equalsIgnoreCase(String.valueOf(result.get("reused")))) {
            String path = firstNonBlank(
                    stringArg(result, "path"),
                    stringArg(result, "existingPath")
            );
            String hint = stringArg(result, "hint");
            return !hint.isBlank() ? hint : AgentObjectReuse.nextStepHint(path, type);
        }
        if (lastStepErrorContains(steps, "object exists:")) {
            String path = latestExistingObjectPath(steps);
            return AgentObjectReuse.nextStepHint(path, type);
        }
        return null;
    }

    private static String latestExistingObjectPath(List<Map<String, Object>> steps) {
        if (steps == null) {
            return "";
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            Map<String, Object> step = steps.get(i);
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            Map<String, Object> result = stepMap(step, "result");
            String path = firstNonBlank(
                    stringArg(result, "existingPath"),
                    stringArg(result, "path")
            );
            if (!path.isBlank()) {
                return path;
            }
            String error = String.valueOf(result.get("error"));
            if (error.startsWith("Object exists:")) {
                return error.substring("Object exists:".length()).trim();
            }
        }
        return "";
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
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
        Map<String, Object> result = stepMap(last, "result");
        if (!"OK".equals(String.valueOf(result.get("status")))) {
            return null;
        }
        if (Boolean.TRUE.equals(result.get("reused"))
                || "true".equalsIgnoreCase(String.valueOf(result.get("reused")))) {
            String hint = stringArg(result, "hint");
            if (!hint.isBlank()) {
                return hint;
            }
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
            case "EVENT_FILTER" -> "EVENT_FILTER created. Reuse path for filter fields; continue the assignment — do not recreate.";
            case "USER" -> """
                    Platform USER created (login + roles). Next assignment step: create the second user if needed, \
                    then set_object_acl on the other user's devices \
                    entries=[{principalType:USER, principalId=<firstUser>, permission:EDITOR}]. \
                    Do NOT apply_mixin platform-user-v1 or spam list_variables on the user.""";
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
