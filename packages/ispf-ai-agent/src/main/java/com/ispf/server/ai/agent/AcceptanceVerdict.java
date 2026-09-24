package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finish gate for an agent turn. Applicable checks are evidence from tool results
 * (and, where the task class is not yet on the plan, a single lexicon).
 * Finish is allowed only when every applicable check is {@link Status#PASS}.
 *
 * <p>{@link Status#FAIL} means the evidence contradicts the check.
 * {@link Status#PARTIAL} means the check is required and has not been observed yet.
 */
public final class AcceptanceVerdict {

    public static final String DOC_REF = "0051";

    public enum Status {
        PASS,
        FAIL,
        PARTIAL
    }

    public record Check(String id, Status status, String summary, String hint) {
        public boolean passed() {
            return status == Status.PASS;
        }

        Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("status", status.name());
            row.put("summary", summary);
            if (hint != null && !hint.isBlank()) {
                row.put("hint", hint);
            }
            row.put("docRef", DOC_REF);
            return row;
        }
    }

    public record Result(Status status, List<Check> checks) {
        public boolean allowsFinish() {
            return status == Status.PASS;
        }

        public Check firstBlocking() {
            for (Check check : checks) {
                if (!check.passed()) {
                    return check;
                }
            }
            return null;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", status.name());
            payload.put("docRef", DOC_REF);
            payload.put("checks", checks.stream().map(Check::toMap).toList());
            return payload;
        }
    }

    private AcceptanceVerdict() {
    }

    public static Result evaluate(List<Map<String, Object>> steps, String userMessage, String assignmentType) {
        if (steps == null || steps.isEmpty()) {
            return new Result(Status.PASS, List.of());
        }
        List<Check> checks = new ArrayList<>();
        add(checks, errors(steps));
        add(checks, devices(steps));
        add(checks, alert(steps, userMessage));
        add(checks, mimic(steps, userMessage));
        add(checks, dashboard(steps));
        add(checks, bundleTests(steps, assignmentType));
        return new Result(aggregate(checks), List.copyOf(checks));
    }

    private static void add(List<Check> checks, Check check) {
        if (check != null) {
            checks.add(check);
        }
    }

    private static Status aggregate(List<Check> checks) {
        boolean partial = false;
        for (Check check : checks) {
            if (check.status() == Status.FAIL) {
                return Status.FAIL;
            }
            if (check.status() == Status.PARTIAL) {
                partial = true;
            }
        }
        return partial ? Status.PARTIAL : Status.PASS;
    }

    private static Check errors(List<Map<String, Object>> steps) {
        List<String> unresolved = AgentTurnToolErrors.unresolvedErrorSummaries(steps);
        if (unresolved.isEmpty()) {
            return passed("errors.none", "No unresolved ERROR tool steps.");
        }
        return failed(
                "errors.none",
                "Cannot finish: turn contains ERROR tool steps: " + String.join("; ", unresolved),
                "Fix failed tools before finish. Never claim success for steps that returned ERROR."
        );
    }

    private static Check devices(List<Map<String, Object>> steps) {
        Set<String> created = new LinkedHashSet<>();
        for (Map<String, Object> step : steps) {
            if (!isToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            Map<String, Object> args = stepMap(step, "arguments");
            Map<String, Object> result = stepMap(step, "result");
            if (!"OK".equals(String.valueOf(result.get("status")))) {
                continue;
            }
            if ("create_object".equals(tool) && "DEVICE".equalsIgnoreCase(String.valueOf(args.get("type")))) {
                String path = String.valueOf(result.get("path"));
                if (!path.isBlank()) {
                    created.add(path);
                }
            }
            if ("create_virtual_device".equals(tool)) {
                created.remove(String.valueOf(result.get("path")));
            }
            if ("apply_mixin_blueprint".equals(tool) && relativeModelVerified(result)) {
                created.remove(String.valueOf(result.get("objectPath")));
            }
        }
        if (created.isEmpty()) {
            return null;
        }
        List<String> unverified = new ArrayList<>();
        boolean observedShortfall = false;
        for (String path : created) {
            Observation observation = deviceObservation(steps, path);
            if (observation == Observation.VERIFIED) {
                continue;
            }
            unverified.add(path);
            if (observation == Observation.SHORTFALL) {
                observedShortfall = true;
            }
        }
        if (unverified.isEmpty()) {
            return passed("device.telemetry", "Created DEVICE paths have verified telemetry variables.");
        }
        String summary = "Cannot finish: DEVICE objects created without verified telemetry variables: "
                + String.join(", ", unverified);
        String hint = "For each path: use create_virtual_device (preferred) OR set templateId virtual-lab-v1|virtual-unified-v1, "
                + "set_variable driverConfigJson with profile, configure_driver with configuration argument "
                + "or after set_variable, driver_control start, then list_variables with count>0.";
        return observedShortfall
                ? failed("device.telemetry", summary, hint)
                : partial("device.telemetry", summary, hint);
    }

    private enum Observation {
        MISSING,
        SHORTFALL,
        VERIFIED
    }

    private static Observation deviceObservation(List<Map<String, Object>> steps, String devicePath) {
        Observation seen = Observation.MISSING;
        for (Map<String, Object> step : steps) {
            if (!isToolStep(step) || !"OK".equals(String.valueOf(stepMap(step, "result").get("status")))) {
                continue;
            }
            String tool = toolName(step);
            Map<String, Object> args = stepMap(step, "arguments");
            Map<String, Object> result = stepMap(step, "result");
            if ("create_virtual_device".equals(tool) && devicePath.equals(String.valueOf(result.get("path")))) {
                Object telemetryCount = result.get("telemetryVariableCount");
                if (telemetryCount instanceof Number number && number.intValue() > 0) {
                    return Observation.VERIFIED;
                }
            }
            if ("apply_mixin_blueprint".equals(tool) && devicePath.equals(String.valueOf(result.get("objectPath")))) {
                if (relativeModelVerified(result)) {
                    return Observation.VERIFIED;
                }
            }
            if ("list_variables".equals(tool) && devicePath.equals(String.valueOf(args.get("path")))) {
                Object count = result.get("count");
                if (count instanceof Number number && number.intValue() > 3) {
                    return Observation.VERIFIED;
                }
                if (count instanceof Number) {
                    seen = Observation.SHORTFALL;
                }
            }
        }
        return seen;
    }

    private static Check alert(List<Map<String, Object>> steps, String userMessage) {
        if (!alertRequired(steps, userMessage)) {
            return null;
        }
        if (hasSuccessfulTool(steps, "configure_alert")) {
            return passed("alert.configured", "configure_alert completed.");
        }
        return partial(
                "alert.configured",
                "Cannot finish: monitoring intent detected but no configure_alert step was completed.",
                "Add configure_alert for the monitored variable (usually on hub/device) before finish. "
                        + "For full chain: configure_alert -> configure_correlator."
        );
    }

    private static Check mimic(List<Map<String, Object>> steps, String userMessage) {
        if (!mimicRequired(steps, userMessage)) {
            return null;
        }
        int elements = AgentToolResultMetrics.lastMimicElementCount(steps);
        if (elements > 0) {
            return passed("mimic.elements", "Mimic diagram elementCount=" + elements + ".");
        }
        if (elements == 0) {
            return failed(
                    "mimic.elements",
                    "Cannot finish: SCADA/MIMIC intent detected and mimic diagram has elementCount=0.",
                    "Use save_mimic_diagram with non-empty elements[], then get_mimic_diagram and verify elementCount>0."
            );
        }
        return partial(
                "mimic.elements",
                "Cannot finish: SCADA/MIMIC created but diagram not verified.",
                "Call save_mimic_diagram with non-empty elements[] or get_mimic_diagram path=<mimicPath> "
                        + "and verify elementCount>0 before finish."
        );
    }

    private static Check dashboard(List<Map<String, Object>> steps) {
        if (!dashboardRequired(steps)) {
            return null;
        }
        if (AgentToolResultMetrics.hasVerifiedDashboardLayout(steps)) {
            return passed("dashboard.widgets", "Dashboard layout has widgetCount>0.");
        }
        String summary = "Cannot finish: DASHBOARD created but layout not verified (widgetCount=0).";
        String hint = "Call get_dashboard_layout path=<dashboardPath> or set_dashboard_layout/add_dashboard_widget "
                + "and verify widgetCount>0 before finish.";
        return dashboardObservedEmpty(steps)
                ? failed("dashboard.widgets", summary, hint)
                : partial("dashboard.widgets", summary, hint);
    }

    private static Check bundleTests(List<Map<String, Object>> steps, String assignmentType) {
        if (assignmentType == null || assignmentType.isBlank()) {
            return null;
        }
        if (AgentAssignmentType.fromString(assignmentType) != AgentAssignmentType.APPLICATION_BUNDLE) {
            return null;
        }
        boolean pass = false;
        boolean failed = false;
        String failedTool = null;
        for (Map<String, Object> step : steps) {
            if (!isToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            if (!"test_function".equals(tool) && !"run_bundle_tests".equals(tool)) {
                continue;
            }
            String status = String.valueOf(stepMap(step, "result").get("status")).toUpperCase(Locale.ROOT);
            if ("PASS".equals(status)) {
                pass = true;
            }
            if ("FAIL".equals(status) || "ERROR".equals(status)) {
                failed = true;
                failedTool = tool;
            }
        }
        if (failed) {
            return failed(
                    "bundle.tests",
                    "Cannot finish: " + failedTool + " returned FAIL.",
                    "Fix the failing bundle test and re-run test_function or run_bundle_tests until status is PASS."
            );
        }
        if (pass) {
            return passed("bundle.tests", "Bundle tests returned PASS.");
        }
        return partial(
                "bundle.tests",
                "Cannot finish: APPLICATION_BUNDLE requires PASS from test_function or run_bundle_tests.",
                "Run test_function or run_bundle_tests and finish only after status PASS."
        );
    }

    private static boolean alertRequired(List<Map<String, Object>> steps, String userMessage) {
        String text = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        boolean monitoringByText = containsAny(
                text,
                "monitor", "monitoring", "монитор", "alert", "алерт", "alarm", "alarming",
                "correlator", "коррелятор"
        );
        boolean dashboardOnly = containsAny(text, "дашборд", "dashboard") && !monitoringByText;
        if (dashboardOnly) {
            return false;
        }
        return monitoringByText
                || hasSuccessfulTool(steps, "configure_alert")
                || hasCreateObjectType(steps, "ALERT")
                || hasSuccessfulTool(steps, "configure_correlator")
                || hasCreateObjectType(steps, "CORRELATOR");
    }

    private static boolean mimicRequired(List<Map<String, Object>> steps, String userMessage) {
        String text = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        return containsAny(text, "scada", "mimic", "мимик", "мнемо", "hmi")
                || hasSuccessfulTool(steps, "save_mimic_diagram")
                || hasSuccessfulTool(steps, "get_mimic_diagram")
                || hasCreateObjectType(steps, "MIMIC");
    }

    private static boolean dashboardRequired(List<Map<String, Object>> steps) {
        return hasSuccessfulTool(steps, "set_dashboard_layout")
                || hasSuccessfulTool(steps, "add_dashboard_widget")
                || hasCreateObjectType(steps, "DASHBOARD");
    }

    private static boolean dashboardObservedEmpty(List<Map<String, Object>> steps) {
        for (Map<String, Object> step : steps) {
            if (!isToolStep(step) || !"OK".equals(String.valueOf(stepMap(step, "result").get("status")))) {
                continue;
            }
            String tool = toolName(step);
            if (!"get_dashboard_layout".equals(tool)
                    && !"set_dashboard_layout".equals(tool)
                    && !"add_dashboard_widget".equals(tool)) {
                continue;
            }
            Map<String, Object> result = stepMap(step, "result");
            if (result.containsKey("widgetCount") || result.containsKey("widgets") || result.containsKey("layoutJson")) {
                if (AgentToolResultMetrics.widgetCountFromResult(result) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean relativeModelVerified(Map<String, Object> result) {
        Object added = result.get("variablesAdded");
        if (added instanceof Number number && number.intValue() > 0) {
            return true;
        }
        Object count = result.get("variableCount");
        return count instanceof Number number && number.intValue() > 3;
    }

    private static boolean hasCreateObjectType(List<Map<String, Object>> steps, String objectType) {
        for (Map<String, Object> step : steps) {
            if (!isToolStep(step) || !"create_object".equals(toolName(step))) {
                continue;
            }
            Map<String, Object> args = stepMap(step, "arguments");
            Map<String, Object> result = stepMap(step, "result");
            if (!"OK".equals(String.valueOf(result.get("status")))) {
                continue;
            }
            if (objectType.equalsIgnoreCase(String.valueOf(args.get("type")))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSuccessfulTool(List<Map<String, Object>> steps, String expectedTool) {
        for (Map<String, Object> step : steps) {
            if (!isToolStep(step) || !expectedTool.equals(toolName(step))) {
                continue;
            }
            if ("OK".equals(String.valueOf(stepMap(step, "result").get("status")))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isToolStep(Map<String, Object> step) {
        return "tool".equals(String.valueOf(step.get("type")));
    }

    private static String toolName(Map<String, Object> step) {
        return String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Check passed(String id, String summary) {
        return new Check(id, Status.PASS, summary, null);
    }

    private static Check failed(String id, String summary, String hint) {
        return new Check(id, Status.FAIL, summary, withDoc(id, hint));
    }

    private static Check partial(String id, String summary, String hint) {
        return new Check(id, Status.PARTIAL, summary, withDoc(id, hint));
    }

    private static String withDoc(String id, String hint) {
        return hint + "\ncheck=" + id + " docRef=" + DOC_REF;
    }
}
