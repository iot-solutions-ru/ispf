package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Progressive tool surface for AI Studio: capability is deferred into packs, not removed.
 * Always-on packs plus meta-tools keep discovery working; the model enables more packs mid-turn.
 */
public final class AgentToolPackCatalog {

    public static final String CORE = "core";
    public static final String DISCOVERY = "discovery";
    public static final String DEVICES = "devices";
    public static final String DASHBOARDS = "dashboards";
    public static final String AUTOMATION = "automation";
    public static final String BUNDLES = "bundles";
    public static final String SCADA = "scada";
    public static final String ANALYTICS = "analytics";
    public static final String SECURITY = "security";
    public static final String MISC = "misc";

    public static final Set<String> ALL_PACKS = Set.of(
            CORE, DISCOVERY, DEVICES, DASHBOARDS, AUTOMATION, BUNDLES, SCADA, ANALYTICS, SECURITY, MISC
    );

    public static final Set<String> ALWAYS_ON = Set.of(CORE, DISCOVERY);

    public static final Set<String> META_TOOLS = Set.of(
            "list_agent_tools",
            "describe_agent_tool",
            "enable_agent_tool_pack"
    );

    private static final Map<String, String> EXPLICIT = buildExplicit();

    private AgentToolPackCatalog() {
    }

    public static String packFor(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return MISC;
        }
        String name = toolName.trim().toLowerCase(Locale.ROOT);
        if (META_TOOLS.contains(name)) {
            return CORE;
        }
        String explicit = EXPLICIT.get(name);
        if (explicit != null) {
            return explicit;
        }
        if (looksLikeDiscovery(name)) {
            return DISCOVERY;
        }
        return MISC;
    }

    public static boolean isMetaTool(String toolName) {
        return toolName != null && META_TOOLS.contains(toolName.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isAlwaysAllowed(String toolName) {
        return isMetaTool(toolName);
    }

    public static Set<String> defaultPacks(AgentInteractionMode mode) {
        LinkedHashSet<String> packs = new LinkedHashSet<>(ALWAYS_ON);
        if (mode != null && mode != AgentInteractionMode.ASK) {
            // Plan / Execute / Auto start lean; keyword hints expand further.
        }
        return Set.copyOf(packs);
    }

    public static Set<String> hintPacks(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Set.of();
        }
        String text = userMessage.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> packs = new LinkedHashSet<>();
        if (containsAny(text,
                "dashboard", "дашборд", "виджет", "widget", "layout", "kpi", "chart")) {
            packs.add(DASHBOARDS);
        }
        if (containsAny(text,
                "device", "устройств", "driver", "snmp", "modbus", "mqtt", "virtual", "sensor",
                "plc", "create_object", "create_virtual", "создай", "создать", "добавь", "настрой")) {
            packs.add(DEVICES);
        }
        if (containsAny(text,
                "workflow", "bpmn", "alert", "алерт", "correlator", "schedule", "binding",
                "automation", "правило", "event", "событ", "function", "функц")) {
            packs.add(AUTOMATION);
        }
        if (containsAny(text,
                "bundle", "import_package", "deploy", "manifest", "application", "приложен",
                "mes-reference", "validate_bundle")) {
            packs.add(BUNDLES);
        }
        if (containsAny(text,
                "mimic", "scada", "мнемо", "hmi", "символ")) {
            packs.add(SCADA);
        }
        if (containsAny(text,
                "analytics", "anomal", "trend", "haystack", "аналит", "period")) {
            packs.add(ANALYTICS);
        }
        if (containsAny(text,
                "security", "role", "permission", "acl", "user", "mfa")) {
            packs.add(SECURITY);
        }
        // Creating dashboards / SCADA / automation almost always needs create_object (devices pack).
        if (packs.contains(DASHBOARDS) || packs.contains(SCADA) || packs.contains(AUTOMATION)) {
            packs.add(DEVICES);
        }
        return Set.copyOf(packs);
    }

    public static List<Map<String, Object>> packIndex() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String pack : List.of(
                CORE, DISCOVERY, DEVICES, DASHBOARDS, AUTOMATION, BUNDLES, SCADA, ANALYTICS, SECURITY, MISC
        )) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pack", pack);
            row.put("description", packDescription(pack));
            row.put("alwaysOn", ALWAYS_ON.contains(pack));
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    public static String packDescription(String pack) {
        return switch (pack == null ? "" : pack) {
            case CORE -> "Meta-tools and core knowledge (search_context, schemas, recipes)";
            case DISCOVERY -> "Read-only inventory: list_/get_/search_/describe_ and similar";
            case DEVICES -> "Create/configure devices, drivers, variables, blueprints";
            case DASHBOARDS -> "Dashboard layouts, widgets, operator UI, reports configure";
            case AUTOMATION -> "Alerts, correlators, workflows, schedules, events, functions";
            case BUNDLES -> "Bundle validate/import/deploy playbooks and app lifecycle";
            case SCADA -> "SCADA mimics and diagram mutations";
            case ANALYTICS -> "Analytics query/expression helpers (mutate side if any)";
            case SECURITY -> "Security-sensitive platform operations";
            case MISC -> "Tools not assigned to a domain pack";
            default -> "Unknown pack";
        };
    }

    public static String shortDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String trimmed = description.trim().replaceAll("\\s+", " ");
        int cut = trimmed.indexOf('.');
        if (cut > 40 && cut < 160) {
            return trimmed.substring(0, cut + 1);
        }
        if (trimmed.length() <= 140) {
            return trimmed;
        }
        return trimmed.substring(0, 137) + "...";
    }

    private static boolean looksLikeDiscovery(String name) {
        return name.startsWith("list_")
                || name.startsWith("get_")
                || name.startsWith("search_")
                || name.startsWith("describe_")
                || name.startsWith("validate_")
                || name.startsWith("dry_run_")
                || name.startsWith("export_")
                || name.startsWith("pull_")
                || name.startsWith("verify_")
                || name.startsWith("evaluate_")
                || name.startsWith("detect_")
                || name.startsWith("compare_")
                || name.startsWith("summarize_")
                || name.startsWith("query_")
                || name.startsWith("read_")
                || name.startsWith("resolve_")
                || "run_report".equals(name)
                || "application_data_status".equals(name);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> buildExplicit() {
        Map<String, String> map = new LinkedHashMap<>();
        // core
        map.put("search_context", CORE);
        map.put("get_automation_schema", CORE);
        map.put("search_platform_recipes", CORE);
        map.put("search_session_context", CORE);
        map.put("get_widget_catalog", CORE);
        map.put("list_agent_tools", CORE);
        map.put("describe_agent_tool", CORE);
        map.put("enable_agent_tool_pack", CORE);

        // devices
        map.put("create_object", DEVICES);
        map.put("delete_object", DEVICES);
        map.put("create_virtual_device", DEVICES);
        map.put("configure_driver", DEVICES);
        map.put("driver_control", DEVICES);
        map.put("create_variable", DEVICES);
        map.put("set_variable", DEVICES);
        map.put("configure_variable_history", DEVICES);
        map.put("create_binding_rule", DEVICES);
        map.put("apply_mixin_blueprint", DEVICES);
        map.put("instantiate_instance_type", DEVICES);
        map.put("ensure_singleton_instance", DEVICES);

        // dashboards / operator presentation
        map.put("set_dashboard_layout", DASHBOARDS);
        map.put("add_dashboard_widget", DASHBOARDS);
        map.put("configure_operator_ui", DASHBOARDS);
        map.put("configure_report", DASHBOARDS);

        // automation
        map.put("configure_alert", AUTOMATION);
        map.put("configure_correlator", AUTOMATION);
        map.put("configure_platform_schedule", AUTOMATION);
        map.put("configure_platform_context_rule", AUTOMATION);
        map.put("fire_event", AUTOMATION);
        map.put("invoke_bff", AUTOMATION);
        map.put("invoke_tree_function", AUTOMATION);
        map.put("deploy_tree_function", AUTOMATION);
        map.put("deploy_app_function", AUTOMATION);
        map.put("run_workflow", AUTOMATION);
        map.put("save_workflow_bpmn", AUTOMATION);
        map.put("update_workflow_status", AUTOMATION);
        map.put("cancel_workflow_instance", AUTOMATION);
        map.put("signal_workflow_instance", AUTOMATION);
        map.put("invoke_workflow_tool", AUTOMATION);

        // bundles / app lifecycle
        map.put("import_package", BUNDLES);
        map.put("register_application", BUNDLES);
        map.put("application_data_migrate", BUNDLES);
        map.put("application_data_seed", BUNDLES);
        map.put("deploy_app_binding", BUNDLES);
        map.put("rollback_application_deploy", BUNDLES);
        map.put("run_deploy_playbook", BUNDLES);
        map.put("deploy_step_validate", BUNDLES);
        map.put("deploy_step_dry_run", BUNDLES);
        map.put("deploy_step_import", BUNDLES);
        map.put("deploy_step_operator_ui", BUNDLES);
        map.put("deploy_step_verify", BUNDLES);
        map.put("deploy_step_finish", BUNDLES);
        map.put("deploy_step_discover", BUNDLES);
        map.put("deploy_step_blueprint", BUNDLES);
        map.put("deploy_step_automation", BUNDLES);

        // scada
        map.put("save_mimic_diagram", SCADA);
        map.put("add_mimic_elements", SCADA);

        // analytics mutations / heavy helpers stay in analytics pack
        map.put("detect_anomalies", ANALYTICS);
        map.put("compare_periods", ANALYTICS);
        map.put("summarize_trend", ANALYTICS);
        map.put("evaluate_analytics_expression", ANALYTICS);
        map.put("query_analytics_tags", ANALYTICS);
        map.put("get_analytics_tag", ANALYTICS);
        map.put("list_analytics_catalog", ANALYTICS);

        // security-ish operator memory write
        map.put("remember_app_memory", SECURITY);

        return Map.copyOf(map);
    }
}
