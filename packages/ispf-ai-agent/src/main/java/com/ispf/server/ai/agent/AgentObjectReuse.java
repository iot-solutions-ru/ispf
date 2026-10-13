package com.ispf.server.ai.agent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * When create_* hits an already-present path, treat it as success and steer the model
 * to configure / continue — not create↔get_object ping-pong.
 */
final class AgentObjectReuse {

    private AgentObjectReuse() {
    }

    static Map<String, Object> alreadyExistsOk(String fullPath, String typeRaw) {
        String path = fullPath == null ? "" : fullPath.trim();
        String type = typeRaw == null ? "" : typeRaw.trim().toUpperCase(Locale.ROOT);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("reused", true);
        result.put("path", path);
        result.put("existingPath", path);
        result.put("message", "Object already exists — reuse this path; do not recreate.");
        result.put("hint", nextStepHint(path, type));
        return result;
    }

    static String nextStepHint(String path, String typeRaw) {
        String pathLabel = (path == null || path.isBlank()) ? "<existingPath>" : path.trim();
        String type = typeRaw == null ? "" : typeRaw.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "ALERT" -> "Object already exists at " + pathLabel
                    + ". Do NOT create_object again and do NOT ping-pong get_object. "
                    + "Next: configure_alert path=" + pathLabel + " with the assignment rules, then continue.";
            case "DASHBOARD" -> "Object already exists at " + pathLabel
                    + ". Next: set_dashboard_layout path=" + pathLabel
                    + " (one call). Do NOT recreate or spam get_object.";
            case "MIMIC" -> "Object already exists at " + pathLabel
                    + ". Next: save_mimic_diagram path=" + pathLabel
                    + ". Do NOT recreate.";
            case "DEVICE" -> "Object already exists at " + pathLabel
                    + ". Next: list_variables path=" + pathLabel
                    + " then configure_driver / bindings as needed. Skip create_object and avoid get_object loops.";
            case "REPORT" -> "Object already exists at " + pathLabel
                    + ". Next: configure_report path=" + pathLabel + ". Do NOT recreate.";
            case "WORKFLOW" -> "Object already exists at " + pathLabel
                    + ". Next: save_workflow_bpmn path=" + pathLabel + ". Do NOT recreate.";
            case "EVENT_FILTER" -> "Object already exists at " + pathLabel
                    + ". Reuse it: set filter fields / continue the assignment. "
                    + "Do NOT create_object again or alternate create_object↔get_object.";
            case "USER", "ROLE" -> "Object already exists at " + pathLabel
                    + ". Reuse for permissions / ACL, then continue the next assignment step. Do NOT recreate.";
            default -> "Object already exists at " + pathLabel
                    + ". Treat as success: configure_*/set_*/list_variables on this path. "
                    + "Do NOT create_object again and do NOT alternate create_object↔get_object.";
        };
    }
}
