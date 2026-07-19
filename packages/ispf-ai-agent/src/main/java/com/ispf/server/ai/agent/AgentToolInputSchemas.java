package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ispf.server.ai.agent.AgentToolSchemas.*;

/**
 * Canonical {@code inputSchema} catalog for platform agent tools (ADR-0051).
 * <p>
 * Every registered tool name has an entry. Prefer declared properties and enums;
 * {@code additionalProperties=true} is soft allowance for optional extras, not an MCP stub.
 */
public final class AgentToolInputSchemas {

    private static final Map<String, Map<String, Object>> BY_NAME = buildCatalog();

    private AgentToolInputSchemas() {
    }

    public static Map<String, Object> forTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return openObject("Unknown tool arguments");
        }
        Map<String, Object> schema = BY_NAME.get(toolName.trim().toLowerCase(Locale.ROOT));
        if (schema != null) {
            return schema;
        }
        return openObject("Arguments for " + toolName + " (see tool description)");
    }

    public static boolean hasCatalogEntry(String toolName) {
        return toolName != null && BY_NAME.containsKey(toolName.trim().toLowerCase(Locale.ROOT));
    }

    public static int catalogSize() {
        return BY_NAME.size();
    }

    private static Map<String, Map<String, Object>> buildCatalog() {
        Map<String, Map<String, Object>> catalog = new LinkedHashMap<>();
        List<String> objectTypes = Arrays.stream(ObjectType.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        List<String> widgetTypes = AgentWidgetCatalog.allTypes();

        catalog.put("add_dashboard_widget", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "widget", widgetObject(widgetTypes)
                ),
                req("path", "widget"),
                true
        ));
        catalog.put("add_mimic_elements", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "elements", arrayProp("Elements to append")
                ),
                req("path", "elements"),
                true
        ));
        catalog.put("application_data_migrate", objectSchema(
                props(
                        "appId", stringProp("Application id")
                ),
                req("appId"),
                true
        ));
        catalog.put("application_data_seed", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "seedJson", stringProp("JSON string payload")
                ),
                req("appId"),
                true
        ));
        catalog.put("application_data_status", objectSchema(
                props(
                        "appId", stringProp("Application id")
                ),
                req("appId"),
                true
        ));
        catalog.put("apply_relative_blueprint", objectSchema(
                props(
                        "objectPath", stringProp("Object tree path"),
                        "blueprintId", stringProp("Relative blueprint id")
                ),
                req("objectPath", "blueprintId"),
                true
        ));
        catalog.put("cancel_workflow_instance", objectSchema(
                props(
                        "instanceId", stringProp("Workflow instance id")
                ),
                req("instanceId"),
                true
        ));
        catalog.put("compare_periods", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "name", stringProp("Node or resource name"),
                        "periodA", stringProp("Period A"),
                        "periodB", stringProp("Period B")
                ),
                req("path", "name"),
                true
        ));
        catalog.put("configure_alert", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "configJson", stringProp("JSON string payload"),
                        "name", stringProp("Node or resource name")
                ),
                req("path"),
                true
        ));
        catalog.put("configure_correlator", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "configJson", stringProp("JSON string payload")
                ),
                req("path"),
                true
        ));
        catalog.put("configure_driver", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "driverId", stringProp("Driver id"),
                        "driverConfigJson", stringProp("JSON string payload"),
                        "pollIntervalMs", integerProp("Poll interval"),
                        "autoStart", booleanProp("Auto-start")
                ),
                req("path"),
                true
        ));
        catalog.put("configure_operator_ui", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "uiJson", stringProp("JSON string payload"),
                        "defaultDashboard", stringProp("Object tree path")
                ),
                List.of(),
                true
        ));
        catalog.put("configure_platform_context_rule", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "ruleJson", stringProp("JSON string payload")
                ),
                req("path"),
                true
        ));
        catalog.put("configure_platform_schedule", objectSchema(
                props(
                        "scheduleId", stringProp("Schedule id"),
                        "intervalMs", integerProp("Interval ms"),
                        "objectPath", stringProp("Object tree path"),
                        "functionName", stringProp("Function name"),
                        "path", stringProp("Object tree path"),
                        "displayName", stringProp("Display name"),
                        "enabled", booleanProp("Enabled")
                ),
                List.of(),
                true
        ));
        catalog.put("configure_report", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "configJson", stringProp("JSON string payload")
                ),
                req("path"),
                true
        ));
        catalog.put("configure_variable_history", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "name", stringProp("Node or resource name"),
                        "historyEnabled", booleanProp("Enable historian")
                ),
                req("path", "name"),
                true
        ));
        catalog.put("create_binding_rule", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "ruleJson", stringProp("JSON string payload")
                ),
                req("path"),
                true
        ));
        catalog.put("create_object", objectSchema(
                props(
                        "parentPath", stringProp("Parent folder path"),
                        "name", stringProp("Node or resource name"),
                        "type", enumProp("ObjectType", objectTypes),
                        "displayName", stringProp("Display name"),
                        "description", stringProp("Optional description"),
                        "templateId", stringProp("Relative blueprint / template id"),
                        "driverId", stringProp("Driver id for DEVICE"),
                        "driverPollIntervalMs", integerProp("Driver poll interval"),
                        "autoStartDriver", booleanProp("Auto-start driver after create")
                ),
                req("parentPath", "name", "type", "displayName"),
                true
        ));
        catalog.put("create_variable", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "name", stringProp("Node or resource name"),
                        "dataType", stringProp("Data type"),
                        "value", stringProp("Initial value"),
                        "writable", booleanProp("Writable")
                ),
                req("path", "name"),
                true
        ));
        catalog.put("create_virtual_device", objectSchema(
                props(
                        "parentPath", stringProp("Parent folder path"),
                        "name", stringProp("Node or resource name"),
                        "displayName", stringProp("Display name"),
                        "profileId", stringProp("Virtual profile id"),
                        "templateId", stringProp("Relative blueprint id")
                ),
                req("parentPath", "name"),
                true
        ));
        catalog.put("delete_object", objectSchema(
                props(
                        "path", stringProp("Object tree path")
                ),
                req("path"),
                true
        ));
        catalog.put("deploy_app_binding", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "bindingJson", stringProp("JSON string payload")
                ),
                req("appId"),
                true
        ));
        catalog.put("deploy_app_function", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "functionName", stringProp("Function name"),
                        "sourceBody", stringProp("JSON string payload")
                ),
                req("appId", "functionName"),
                true
        ));
        catalog.put("deploy_step_dry_run", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "manifestJson", stringProp("JSON string payload")
                ),
                List.of(),
                true
        ));
        catalog.put("deploy_step_import", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "manifestJson", stringProp("JSON string payload")
                ),
                List.of(),
                true
        ));
        catalog.put("deploy_step_operator_ui", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "uiJson", stringProp("JSON string payload")
                ),
                req("appId"),
                true
        ));
        catalog.put("deploy_step_validate", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "manifestJson", stringProp("JSON string payload")
                ),
                List.of(),
                true
        ));
        catalog.put("deploy_step_verify", objectSchema(
                props(
                        "appId", stringProp("Application id")
                ),
                req("appId"),
                true
        ));
        catalog.put("deploy_step_finish", objectSchema(
                props(
                        "appId", stringProp("Application id")
                ),
                List.of(),
                true
        ));
        catalog.put("deploy_step_discover", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "query", stringProp("Discovery query")
                ),
                List.of(),
                true
        ));
        catalog.put("deploy_step_blueprint", objectSchema(
                props(
                        "appId", stringProp("Application id")
                ),
                List.of(),
                true
        ));
        catalog.put("deploy_step_automation", objectSchema(
                props(
                        "appId", stringProp("Application id")
                ),
                List.of(),
                true
        ));
        catalog.put("deploy_tree_function", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "functionName", stringProp("Function name"),
                        "sourceType", stringProp("Source type"),
                        "sourceBody", stringProp("JSON string payload"),
                        "inputSchema", objectProp("Input schema"),
                        "outputSchema", objectProp("Output schema")
                ),
                req("path", "functionName", "sourceType", "sourceBody"),
                true
        ));
        catalog.put("describe_variables", objectSchema(
                props(
                        "path", stringProp("Object tree path")
                ),
                req("path"),
                true
        ));
        catalog.put("detect_anomalies", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "name", stringProp("Node or resource name")
                ),
                req("path", "name"),
                true
        ));
        catalog.put("driver_control", objectSchema(
                props(
                        "devicePath", stringProp("Device object path"),
                        "path", stringProp("Alias for devicePath"),
                        "action", enumProp("Driver action", List.of("status", "start", "stop", "poll"))
                ),
                req("action"),
                true
        ));
        catalog.put("dry_run_deploy", objectSchema(
                props(
                        "manifestJson", stringProp("JSON string payload"),
                        "appId", stringProp("Application id")
                ),
                List.of(),
                true
        ));
        catalog.put("ensure_absolute_instance", objectSchema(
                props(
                        "blueprintId", stringProp("Absolute blueprint id")
                ),
                req("blueprintId"),
                true
        ));
        catalog.put("evaluate_analytics_expression", objectSchema(
                props(
                        "expression", stringProp("Analytics expression"),
                        "bindings", objectProp("Bindings")
                ),
                req("expression"),
                true
        ));
        catalog.put("export_application_bundle", objectSchema(
                props(
                        "appId", stringProp("Application id")
                ),
                req("appId"),
                true
        ));
        catalog.put("export_haystack", objectSchema(
                props(
                        "rootPath", stringProp("Object tree path"),
                        "includePoints", booleanProp("Include points")
                ),
                List.of(),
                true
        ));
        catalog.put("fire_event", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "eventType", stringProp("Event type"),
                        "payload", objectProp("Payload")
                ),
                req("path", "eventType"),
                true
        ));
        catalog.put("get_analytics_tag", objectSchema(
                props(
                        "tagId", stringProp("Analytics tag id"),
                        "path", stringProp("Object tree path")
                ),
                List.of(),
                true
        ));
        catalog.put("get_automation_schema", objectSchema(
                props(
                        "topic", stringProp("Schema topic"),
                        "offset", integerProp("Offset"),
                        "limit", integerProp("Limit")
                ),
                List.of(),
                true
        ));
        catalog.put("get_dashboard_layout", objectSchema(
                props(
                        "path", stringProp("Object tree path")
                ),
                req("path"),
                true
        ));
        catalog.put("get_deploy_playbook", objectSchema(
                props(
                        "playbookId", stringProp("Playbook id")
                ),
                List.of(),
                true
        ));
        catalog.put("get_driver_help", objectSchema(
                props(
                        "driverId", stringProp("Driver id")
                ),
                req("driverId"),
                true
        ));
        catalog.put("get_event_schema", objectSchema(
                props(
                        "eventType", stringProp("Event type"),
                        "appId", stringProp("Application id")
                ),
                List.of(),
                true
        ));
        catalog.put("get_example_bundle", objectSchema(
                props(
                        "exampleId", stringProp("Example id"),
                        "name", stringProp("Example name")
                ),
                List.of(),
                true
        ));
        catalog.put("get_function", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "appId", stringProp("Application id"),
                        "functionName", stringProp("Function name")
                ),
                List.of(),
                true
        ));
        catalog.put("get_function_template", objectSchema(
                props(
                        "templateId", stringProp("Template id")
                ),
                List.of(),
                true
        ));
        catalog.put("get_mimic_diagram", objectSchema(
                props(
                        "path", stringProp("Object tree path")
                ),
                req("path"),
                true
        ));
        catalog.put("get_object", objectSchema(
                props(
                        "path", stringProp("Object tree path")
                ),
                req("path"),
                true
        ));
        catalog.put("get_object_blueprint", objectSchema(
                props(
                        "id", stringProp("Blueprint id"),
                        "name", stringProp("Blueprint name")
                ),
                List.of(),
                true
        ));
        catalog.put("get_operator_link", objectSchema(
                props(
                        "kind", stringProp("Link kind"),
                        "path", stringProp("Object tree path")
                ),
                List.of(),
                true
        ));
        catalog.put("get_operator_scope", emptyObject());
        catalog.put("get_report_schema", objectSchema(
                props(
                        "path", stringProp("Object tree path")
                ),
                req("path"),
                false
        ));
        catalog.put("get_variable_history", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "name", stringProp("Node or resource name"),
                        "from", stringProp("ISO start"),
                        "to", stringProp("ISO end"),
                        "limit", integerProp("Max points")
                ),
                req("path", "name"),
                true
        ));
        catalog.put("get_variable_trend", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "name", stringProp("Node or resource name"),
                        "window", stringProp("Window")
                ),
                req("path", "name"),
                true
        ));
        catalog.put("get_widget_catalog", objectSchema(
                props(
                        "type", enumProp("Widget type filter", widgetTypes),
                        "binding", stringProp("Binding class filter")
                ),
                List.of(),
                true
        ));
        catalog.put("get_workflow", objectSchema(
                props(
                        "path", stringProp("Object tree path")
                ),
                req("path"),
                true
        ));
        catalog.put("import_package", objectSchema(
                props(
                        "manifestJson", stringProp("JSON string payload"),
                        "appId", stringProp("Application id")
                ),
                List.of(),
                true
        ));
        catalog.put("instantiate_instance_type", objectSchema(
                props(
                        "parentPath", stringProp("Parent folder path"),
                        "typeId", stringProp("Instance type id"),
                        "name", stringProp("Node or resource name"),
                        "displayName", stringProp("Display name")
                ),
                req("parentPath", "typeId", "name"),
                true
        ));
        catalog.put("invoke_bff", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "functionName", stringProp("Function name"),
                        "input", objectProp("Input record")
                ),
                req("appId", "functionName"),
                true
        ));
        catalog.put("invoke_tree_function", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "functionName", stringProp("Function name"),
                        "input", objectProp("Input record")
                ),
                req("path", "functionName"),
                true
        ));
        catalog.put("invoke_workflow_tool", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "input", objectProp("Typed workflow input")
                ),
                req("path"),
                true
        ));
        catalog.put("list_absolute_blueprints", emptyObject());
        catalog.put("list_analytics_catalog", emptyObject());
        catalog.put("list_app_bindings", objectSchema(
                props(
                        "appId", stringProp("Application id")
                ),
                req("appId"),
                false
        ));
        catalog.put("list_app_documents", objectSchema(
                props(
                        "limit", integerProp("Max documents (default 30)")
                ),
                List.of(),
                true
        ));
        catalog.put("list_app_memory", objectSchema(
                props(
                        "query", stringProp("Optional search query"),
                        "limit", integerProp("Max entries (default 20)")
                ),
                List.of(),
                true
        ));
        catalog.put("list_applications", emptyObject());
        catalog.put("list_automation", objectSchema(
                props(
                        "parent", stringProp("Parent folder path")
                ),
                List.of(),
                true
        ));
        catalog.put("list_binding_rules", objectSchema(
                props(
                        "path", stringProp("Object tree path")
                ),
                req("path"),
                false
        ));
        catalog.put("list_drivers", emptyObject());
        catalog.put("list_event_catalog", objectSchema(
                props(
                        "appId", stringProp("Application id")
                ),
                List.of(),
                true
        ));
        catalog.put("list_events", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "limit", integerProp("Max events")
                ),
                List.of(),
                true
        ));
        catalog.put("list_examples", emptyObject());
        catalog.put("list_functions", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "path", stringProp("Object tree path")
                ),
                List.of(),
                true
        ));
        catalog.put("list_instance_types", emptyObject());
        catalog.put("list_mimic_symbols", emptyObject());
        catalog.put("list_object_blueprints", objectSchema(
                props(
                        "objectType", stringProp("Filter by ObjectType")
                ),
                List.of(),
                true
        ));
        catalog.put("list_objects", objectSchema(
                props(
                        "parent", stringProp("Parent folder path"),
                        "parentPath", stringProp("Parent folder path"),
                        "lite", booleanProp("Lite DTOs (default true)")
                ),
                List.of(),
                true
        ));
        catalog.put("list_platform_schedules", emptyObject());
        catalog.put("list_relative_blueprints", objectSchema(
                props(
                        "objectType", stringProp("Filter by ObjectType")
                ),
                List.of(),
                true
        ));
        catalog.put("list_reports", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "parent", stringProp("Parent folder path")
                ),
                List.of(),
                true
        ));
        catalog.put("list_variables", objectSchema(
                props(
                        "path", stringProp("Object tree path")
                ),
                req("path"),
                true
        ));
        catalog.put("list_virtual_profiles", emptyObject());
        catalog.put("list_work_queue", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "limit", integerProp("Max tasks")
                ),
                List.of(),
                true
        ));
        catalog.put("list_workflow_instances", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "limit", integerProp("Max rows")
                ),
                List.of(),
                true
        ));
        catalog.put("list_workflow_steps", objectSchema(
                props(
                        "instanceId", stringProp("Workflow instance id"),
                        "path", stringProp("Object tree path")
                ),
                List.of(),
                true
        ));
        catalog.put("pull_application_from_tree", objectSchema(
                props(
                        "appId", stringProp("Application id")
                ),
                req("appId"),
                true
        ));
        catalog.put("query_analytics_tags", objectSchema(
                props(
                        "query", stringProp("Search query"),
                        "limit", integerProp("Max tags")
                ),
                List.of(),
                true
        ));
        catalog.put("read_app_document", objectSchema(
                props(
                        "path", stringProp("Document path"),
                        "id", stringProp("Document id")
                ),
                List.of(),
                true
        ));
        catalog.put("register_application", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "displayName", stringProp("Display name"),
                        "schemaName", stringProp("SQL schema")
                ),
                req("appId"),
                true
        ));
        catalog.put("remember_app_memory", objectSchema(
                props(
                        "content", stringProp("Durable knowledge to store"),
                        "kind", enumProp(
                                "Memory kind",
                                List.of("fact", "glossary", "preference", "playbook", "correction")
                        ),
                        "topic", stringProp("Short topic / title (defaults from content)")
                ),
                req("content"),
                true
        ));
        catalog.put("resolve_timezone", objectSchema(
                props(
                        "objectPath", stringProp("Object tree path")
                ),
                req("objectPath"),
                true
        ));
        catalog.put("rollback_application_deploy", objectSchema(
                props(
                        "appId", stringProp("Application id"),
                        "snapshotId", stringProp("Snapshot id")
                ),
                req("appId"),
                true
        ));
        catalog.put("run_deploy_playbook", objectSchema(
                props(
                        "playbookId", stringProp("Playbook id"),
                        "step", stringProp("Step id")
                ),
                List.of(),
                true
        ));
        catalog.put("run_report", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "parameters", objectProp("Report parameters")
                ),
                req("path"),
                true
        ));
        catalog.put("run_workflow", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "input", objectProp("Workflow input map")
                ),
                req("path"),
                true
        ));
        catalog.put("save_mimic_diagram", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "diagramJson", stringProp("JSON string payload"),
                        "elements", arrayProp("Mimic elements")
                ),
                req("path"),
                true
        ));
        catalog.put("save_workflow_bpmn", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "bpmnXml", stringProp("BPMN XML"),
                        "bpmnJson", stringProp("JSON string payload")
                ),
                req("path"),
                true
        ));
        catalog.put("search_app_documents", objectSchema(
                props(
                        "query", stringProp("Search query")
                ),
                req("query"),
                true
        ));
        catalog.put("search_by_haystack_tags", objectSchema(
                props(
                        "tags", stringProp("Comma-separated or JSON tags"),
                        "rootPath", stringProp("Object tree path")
                ),
                List.of(),
                true
        ));
        catalog.put("search_context", objectSchema(
                props(
                        "query", stringProp("Search query"),
                        "topic", stringProp("ContextPack topic id"),
                        "limit", integerProp("Max slices")
                ),
                List.of(),
                true
        ));
        catalog.put("search_objects", objectSchema(
                props(
                        "query", stringProp("Search query"),
                        "parent", stringProp("Parent folder path"),
                        "limit", integerProp("Max results")
                ),
                List.of(),
                true
        ));
        catalog.put("search_platform_recipes", objectSchema(
                props(
                        "query", stringProp("Search query"),
                        "category", stringProp("Category"),
                        "industry", stringProp("Industry"),
                        "archetype", stringProp("Archetype")
                ),
                List.of(),
                true
        ));
        catalog.put("search_session_context", objectSchema(
                props(
                        "query", stringProp("Search query")
                ),
                List.of(),
                true
        ));
        catalog.put("set_dashboard_layout", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "layoutJson", stringProp("JSON string payload"),
                        "template", stringProp("Layout template name")
                ),
                req("path"),
                true
        ));
        catalog.put("set_variable", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "name", stringProp("Node or resource name"),
                        "value", stringProp("Variable value"),
                        "quality", stringProp("Quality flag")
                ),
                req("path", "name"),
                true
        ));
        catalog.put("signal_workflow_instance", objectSchema(
                props(
                        "instanceId", stringProp("Instance id"),
                        "signal", stringProp("Signal name"),
                        "payload", objectProp("Payload")
                ),
                req("instanceId", "signal"),
                true
        ));
        catalog.put("summarize_trend", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "name", stringProp("Node or resource name"),
                        "summary", stringProp("Server summary payload")
                ),
                List.of(),
                true
        ));
        catalog.put("update_workflow_status", objectSchema(
                props(
                        "path", stringProp("Object tree path"),
                        "status", enumProp("Workflow status", List.of("DRAFT", "ACTIVE", "DISABLED"))
                ),
                req("path", "status"),
                true
        ));
        catalog.put("validate_bundle", objectSchema(
                props(
                        "manifestJson", stringProp("JSON string payload"),
                        "appId", stringProp("Application id")
                ),
                List.of(),
                true
        ));
        return Map.copyOf(catalog);
    }

    private static Map<String, Object> widgetObject(List<String> widgetTypes) {
        Map<String, Object> widget = objectProp("Widget object");
        widget.put("properties", props(
                "id", stringProp("Widget id"),
                "type", enumProp("Widget type", widgetTypes),
                "title", stringProp("Title"),
                "x", integerProp("Grid x"),
                "y", integerProp("Grid y"),
                "w", integerProp("Grid width"),
                "h", integerProp("Grid height"),
                "objectPath", stringProp("Bound object path"),
                "selectionKey", stringProp("Selection key"),
                "variableName", stringProp("Variable name"),
                "valueField", stringProp("Value field"),
                "parentPath", stringProp("Parent path for catalog widgets"),
                "mimicPath", stringProp("Mimic path for scada-mimic"),
                "reportPath", stringProp("Report path"),
                "functionName", stringProp("Function name")
        ));
        widget.put("required", List.of("type"));
        return widget;
    }
}
