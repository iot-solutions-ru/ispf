package com.ispf.server.security;

import com.ispf.server.security.OperatorAgentToolAllowlist;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BL-179: default operator-agent tool allowlists and ISA-95 scope prefixes for role templates.
 */
public final class PlatformRoleTemplatePermissions {

    public static final String OPERATOR_AGENT_TOOLS_VAR = "operatorAgentTools";
    public static final String SCOPE_PATH_PREFIXES_VAR = "scopePathPrefixes";

    /** Read-only operator — HMI and historian without reports, BFF invoke, or work queue. */
    public static final Set<String> OPERATOR_READONLY_TOOLS = Set.of(
            "get_operator_scope",
            "list_objects",
            "get_object",
            "search_objects",
            "search_by_haystack_tags",
            "list_variables",
            "describe_variables",
            "list_events",
            "get_event_schema",
            "list_event_catalog",
            "get_dashboard_layout",
            "list_automation",
            "get_automation_schema",
            "get_variable_history",
            "get_variable_trend",
            "list_app_documents",
            "read_app_document",
            "search_app_documents",
            "get_operator_link"
    );

    /** MES supervisor — full operator copilot allowlist (reports, work queue, BFF, memory). */
    public static final Set<String> MES_SUPERVISOR_TOOLS = OperatorAgentToolAllowlist.ALLOWED_TOOLS;

    private static final List<String> MES_SUPERVISOR_SCOPE_PREFIXES = List.of(
            "root.platform.mes",
            "root.platform.applications",
            "root.platform.dashboards",
            "root.platform.reports",
            "root.platform.workflows",
            "root.platform.instances",
            "root.platform.devices"
    );

    private static final List<String> OPERATOR_READONLY_SCOPE_PREFIXES = List.of(
            "root.platform.dashboards",
            "root.platform.devices",
            "root.platform.mimics"
    );

    private PlatformRoleTemplatePermissions() {
    }

    public static boolean isTemplate(String roleName) {
        return PlatformRoleService.OPERATOR_READONLY.equals(roleName)
                || PlatformRoleService.MES_SUPERVISOR.equals(roleName);
    }

    public static Optional<Set<String>> operatorAgentTools(String roleName) {
        if (PlatformRoleService.OPERATOR_READONLY.equals(roleName)) {
            return Optional.of(Set.copyOf(OPERATOR_READONLY_TOOLS));
        }
        if (PlatformRoleService.MES_SUPERVISOR.equals(roleName)) {
            return Optional.of(Set.copyOf(MES_SUPERVISOR_TOOLS));
        }
        return Optional.empty();
    }

    public static Optional<List<String>> scopePathPrefixes(String roleName) {
        if (PlatformRoleService.OPERATOR_READONLY.equals(roleName)) {
            return Optional.of(OPERATOR_READONLY_SCOPE_PREFIXES);
        }
        if (PlatformRoleService.MES_SUPERVISOR.equals(roleName)) {
            return Optional.of(MES_SUPERVISOR_SCOPE_PREFIXES);
        }
        return Optional.empty();
    }

    /**
     * Union of template tool allowlists for the given role names, intersected with the global operator allowlist.
     */
    public static Set<String> unionOperatorAgentTools(Iterable<String> roleNames) {
        Set<String> union = new LinkedHashSet<>();
        for (String roleName : roleNames) {
            operatorAgentTools(roleName).ifPresent(union::addAll);
        }
        if (union.isEmpty()) {
            return Set.copyOf(OperatorAgentToolAllowlist.ALLOWED_TOOLS);
        }
        Set<String> filtered = new LinkedHashSet<>();
        for (String tool : union) {
            if (OperatorAgentToolAllowlist.isAllowed(tool)) {
                filtered.add(tool);
            }
        }
        return Set.copyOf(filtered);
    }
}
