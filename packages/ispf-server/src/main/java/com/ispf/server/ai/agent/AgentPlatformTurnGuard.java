package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Blocks premature agent finish when device provisioning was not verified.
 */
final class AgentPlatformTurnGuard {

    private AgentPlatformTurnGuard() {
    }

    record BlockDecision(String error, String hint) {
        boolean blocked() {
            return error != null && !error.isBlank();
        }
    }

    static Optional<BlockDecision> checkBeforeFinish(List<Map<String, Object>> steps) {
        return checkBeforeFinish(steps, "");
    }

    static Optional<BlockDecision> checkBeforeFinish(List<Map<String, Object>> steps, String userMessage) {
        if (steps == null || steps.isEmpty()) {
            return Optional.empty();
        }
        Optional<BlockDecision> deviceBlock = checkDeviceVerification(steps);
        if (deviceBlock.isPresent()) {
            return deviceBlock;
        }
        if (requiresMonitoringAlert(steps, userMessage) && !hasSuccessfulTool(steps, "configure_alert")) {
            return Optional.of(new BlockDecision(
                    "Cannot finish: monitoring intent detected but no configure_alert step was completed.",
                    "Add configure_alert for the monitored variable (usually on hub/device) before finish. "
                            + "For full chain: configure_alert -> configure_correlator."
            ));
        }
        if (requiresScadaMimicValidation(steps, userMessage) && hasEmptyMimicDiagram(steps)) {
            return Optional.of(new BlockDecision(
                    "Cannot finish: SCADA/MIMIC intent detected and get_mimic_diagram returned elementCount=0.",
                    "Use save_mimic_diagram with non-empty elements[], then get_mimic_diagram and verify elementCount>0."
            ));
        }
        return Optional.empty();
    }

    private static Optional<BlockDecision> checkDeviceVerification(List<Map<String, Object>> steps) {
        Set<String> devicePaths = new LinkedHashSet<>();
        for (Map<String, Object> step : steps) {
            if (!isToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            Map<String, Object> args = stepMap(step, "arguments");
            Map<String, Object> result = stepMap(step, "result");

            if ("create_object".equals(tool) && "DEVICE".equalsIgnoreCase(String.valueOf(args.get("type")))) {
                if ("OK".equals(String.valueOf(result.get("status")))) {
                    String path = String.valueOf(result.get("path"));
                    if (!path.isBlank()) {
                        devicePaths.add(path);
                    }
                }
            }
            if ("create_virtual_device".equals(tool) && "OK".equals(String.valueOf(result.get("status")))) {
                String path = String.valueOf(result.get("path"));
                if (!path.isBlank()) {
                    devicePaths.remove(path);
                }
            }
            if ("apply_relative_model".equals(tool) && "OK".equals(String.valueOf(result.get("status")))) {
                String path = String.valueOf(result.get("objectPath"));
                if (!path.isBlank() && relativeModelVerified(result)) {
                    devicePaths.remove(path);
                }
            }
        }
        if (devicePaths.isEmpty()) return Optional.empty();
        List<String> unverified = new ArrayList<>();
        for (String path : devicePaths) {
            if (!hasVerifiedVariables(steps, path)) {
                unverified.add(path);
            }
        }
        if (unverified.isEmpty()) return Optional.empty();
        String paths = String.join(", ", unverified);
        return Optional.of(new BlockDecision(
                "Cannot finish: DEVICE objects created without verified telemetry variables: " + paths,
                "For each path: use create_virtual_device (preferred) OR set templateId virtual-lab-v1|virtual-unified-v1, "
                        + "set_variable driverConfigJson with profile, configure_driver with configuration argument "
                        + "or after set_variable, driver_control start, then list_variables with count>0."
        ));
    }

    private static boolean hasVerifiedVariables(List<Map<String, Object>> steps, String devicePath) {
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
            if ("create_virtual_device".equals(tool) && devicePath.equals(String.valueOf(result.get("path")))) {
                Object telemetryCount = result.get("telemetryVariableCount");
                if (telemetryCount instanceof Number number && number.intValue() > 0) {
                    return true;
                }
            }
            if ("apply_relative_model".equals(tool) && devicePath.equals(String.valueOf(result.get("objectPath")))) {
                Object added = result.get("variablesAdded");
                Object count = result.get("variableCount");
                if (added instanceof Number number && number.intValue() > 0) {
                    return true;
                }
                if (count instanceof Number number && number.intValue() > 3) {
                    return true;
                }
            }
            if ("list_variables".equals(tool) && devicePath.equals(String.valueOf(args.get("path")))) {
                Object count = result.get("count");
                if (count instanceof Number number && number.intValue() > 3) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean requiresMonitoringAlert(List<Map<String, Object>> steps, String userMessage) {
        String text = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        boolean monitoringByText = containsAny(
                text,
                "monitor", "monitoring", "монитор", "дашборд", "dashboard",
                "alert", "алерт", "alarm", "alarming", "correlator", "коррелятор"
        );
        boolean dashboardPattern = hasSuccessfulTool(steps, "set_dashboard_layout")
                || hasSuccessfulTool(steps, "add_dashboard_widget")
                || hasCreateObjectType(steps, "DASHBOARD");
        boolean alertPattern = hasSuccessfulTool(steps, "configure_alert") || hasCreateObjectType(steps, "ALERT");
        boolean correlatorPattern = hasSuccessfulTool(steps, "configure_correlator")
                || hasCreateObjectType(steps, "CORRELATOR");
        return monitoringByText || dashboardPattern || alertPattern || correlatorPattern;
    }

    private static boolean requiresScadaMimicValidation(List<Map<String, Object>> steps, String userMessage) {
        String text = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        boolean scadaByText = containsAny(text, "scada", "mimic", "мимик", "мнемо", "hmi");
        boolean scadaPattern = hasSuccessfulTool(steps, "save_mimic_diagram")
                || hasSuccessfulTool(steps, "get_mimic_diagram")
                || hasCreateObjectType(steps, "MIMIC");
        return scadaByText || scadaPattern;
    }

    private static boolean hasEmptyMimicDiagram(List<Map<String, Object>> steps) {
        for (Map<String, Object> step : steps) {
            if (!isToolStep(step) || !"get_mimic_diagram".equals(toolName(step))) {
                continue;
            }
            Map<String, Object> result = stepMap(step, "result");
            if (!"OK".equals(String.valueOf(result.get("status")))) {
                continue;
            }
            Integer elementCount = toInt(result.get("elementCount"));
            if (elementCount != null && elementCount == 0) {
                return true;
            }
        }
        return false;
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
            Map<String, Object> result = stepMap(step, "result");
            if ("OK".equals(String.valueOf(result.get("status")))) {
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

    private static Integer toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean relativeModelVerified(Map<String, Object> result) {
        Object added = result.get("variablesAdded");
        if (added instanceof Number number && number.intValue() > 0) {
            return true;
        }
        Object count = result.get("variableCount");
        return count instanceof Number number && number.intValue() > 3;
    }
}
