package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Map;

/**
 * Builds the agent system prompt without {@link String#formatted(String, Object...)} on playbook text
 * (playbooks may contain {@code %} and other characters that break format strings).
 */
public final class AgentPromptBuilder {

    private static final String HEADER = """
            You are the ISPF platform agent — a helpful admin copilot for the object tree.
            The user speaks in plain language (often Russian). Your finish summary MUST be in the same language,
            friendly and non-technical: explain what was created/found and where to open it in the UI.
            You may receive prior turns in this chat — use them for follow-up requests (e.g. "add dashboard for that device").
            
            Work step-by-step using platform tools. For devices, drivers, dashboards — use tree tools first.
            search_context at most once per topic; for dashboard layout use get_dashboard_layout / set_dashboard_layout.
            
            Reply with ONLY one JSON object per turn — no markdown fences, no prose before or after:
            {"type":"tool","name":"<tool>","arguments":{...}}
            or when done:
            {"type":"finish","summary":"Human-readable result for the user","result":{"devicePath":"...","dashboardPath":"..."}}
            
            """;

    private static final String RULES = """
            
            Rules:
            - create_object types: DEVICE, DASHBOARD, CUSTOM, WORKFLOW, REPORT, ALERT, CORRELATOR, ...
            - delete_object path=<full path> — remove tree node; stops device driver first
            - SNMP device: templateId snmp-agent-v1, driverId snmp, host 127.0.0.1:161 community public
            - set_variable for driverConfigJson, driverPointMappingsJson, dashboard title
            - Dashboard layout: variable name is layout (JSON string with widgets[]). NEVER set_variable name=widgets.
              Use get_dashboard_layout, set_dashboard_layout (or template=snmp-host-monitoring), add_dashboard_widget.
            - configure_driver or driver_control start after SNMP mappings are set
            - list_variables to show metrics to the user in finish summary
            - bundle import only after validate_bundle/dry_run_deploy OK
            - Never invent REST paths; use tools only
            """;

    private AgentPromptBuilder() {
    }

    public static String build(String rootPath, List<Map<String, Object>> toolCatalog) {
        String effectiveRoot = rootPath == null || rootPath.isBlank() ? "root" : rootPath.trim();
        StringBuilder prompt = new StringBuilder(HEADER.length() + 4096);
        prompt.append(HEADER);
        prompt.append("Default tree root for this run: ").append(effectiveRoot).append("\n\n");
        prompt.append("Available tools:\n");
        for (Map<String, Object> tool : toolCatalog) {
            prompt.append("- ")
                    .append(tool.get("name"))
                    .append(": ")
                    .append(tool.get("description"))
                    .append("\n");
        }
        prompt.append("\nPlaybooks:\n");
        prompt.append(AgentPlaybooks.snmpLocalhostMonitoring());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.dashboardLayoutEditing());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.snmpIfMibExtension());
        prompt.append(RULES);
        prompt.append("- Reuse existing demo paths when present: ")
                .append(AgentPlaybooks.SNMP_DEVICE_PATH)
                .append(" and ")
                .append(AgentPlaybooks.SNMP_DASHBOARD_PATH)
                .append("\n");
        return prompt.toString();
    }
}
