package com.ispf.server.ai.agent;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * Hard allowlist for operator copilot (BL-109). Read-only / report tools only — no tree mutations.
 */
public final class OperatorAgentToolAllowlist {

    /** Tools exposed to {@link AgentProfile#OPERATOR} catalog and execute path. */
    public static final Set<String> ALLOWED_TOOLS = Set.of(
            "get_operator_scope",
            "list_objects",
            "get_object",
            "search_objects",
            "search_by_haystack_tags",
            "list_variables",
            "describe_variables",
            "list_functions",
            "get_function",
            "invoke_bff",
            "invoke_tree_function",
            "list_events",
            "get_event_schema",
            "list_event_catalog",
            "list_reports",
            "get_report_schema",
            "run_report",
            "get_dashboard_layout",
            "list_automation",
            "get_automation_schema",
            "get_variable_history",
            "get_variable_trend",
            "list_work_queue",
            "list_app_memory",
            "remember_app_memory",
            "list_app_documents",
            "read_app_document",
            "search_app_documents",
            "get_operator_link"
    );

    /** Representative mutating tools — must never appear in operator allowlist. */
    static final Set<String> MUTATING_TOOL_SAMPLES = Set.of(
            "create_object",
            "delete_object",
            "configure_driver",
            "driver_control",
            "set_dashboard_layout",
            "add_dashboard_widget",
            "save_mimic_diagram",
            "validate_bundle",
            "dry_run_deploy",
            "import_package",
            "deploy_app_binding",
            "configure_alert"
    );

    private OperatorAgentToolAllowlist() {
    }

    public static boolean isAllowed(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        return ALLOWED_TOOLS.contains(toolName.trim().toLowerCase(Locale.ROOT));
    }

    public static Set<String> allowedTools() {
        return Collections.unmodifiableSet(ALLOWED_TOOLS);
    }
}
