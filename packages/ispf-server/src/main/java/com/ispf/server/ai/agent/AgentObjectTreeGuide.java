package com.ispf.server.ai.agent;

import com.ispf.plugin.model.ModelCatalogRoots;
import com.ispf.server.automation.AutomationTreeService;

import java.util.List;
import java.util.Map;

/**
 * Platform object-tree paths and create_object conventions for the agent.
 */
public final class AgentObjectTreeGuide {

    private AgentObjectTreeGuide() {
    }

    public static String referenceText() {
        return """
                ### Object tree (platform paths)
                
                | Path | Type folder | create_object type |
                |------|-------------|-------------------|
                | root.platform.devices | Devices | DEVICE |
                | root.platform.dashboards | Dashboards | DASHBOARD (templateId dashboard-v1) |
                | root.platform.workflows | Workflows | WORKFLOW |
                | root.platform.reports | Reports | REPORT |
                | """
                + "| "
                + AutomationTreeService.ALERT_RULES_ROOT
                + " | Alert rules | ALERT |\n"
                + "| "
                + AutomationTreeService.CORRELATORS_ROOT
                + " | Correlators | CORRELATOR |\n"
                + """
                | root.platform.relative-models | Relative model catalog | (definitions only) |
                | root.platform.instance-types | Instance type catalog | (definitions only) |
                | root.platform.absolute-models | Absolute model catalog | (definitions only) |
                | root.platform.instances | Model instances | CUSTOM from instance type |
                
                Legacy `root.platform.models` removed — use typed catalogs above.
                
                ### Object types & key variables
                
                - **DEVICE**: driverId, driverConfigJson, driverPointMappingsJson; poll via configure_driver / driver_control
                - **DASHBOARD**: title, layout (JSON widgets[]), refreshIntervalMs — NOT a variable named widgets
                - **CUSTOM**: hub for refAt bindings, aggregations, clusterError logic
                - **ALERT**: targetObjectPath, watchVariable, conditionExpr (CEL), eventName — tool configure_alert
                - **CORRELATOR**: patternType COUNT|SEQUENCE|EVENT_CHAIN — tool configure_correlator
                - **WORKFLOW**: BPMN in bundle or platform tree
                - **REPORT**: SQL report definition path for report widget
                
                ### Models & templates
                
                - list_object_models — templateId for create_object (device-v1, dashboard-v1, snmp-agent-v1, virtual-lab-v1, …)
                - Model catalogs: """
                + ModelCatalogRoots.RELATIVE
                + ", "
                + ModelCatalogRoots.INSTANCE
                + ", "
                + ModelCatalogRoots.ABSOLUTE
                + """
                
                - create_variable + create_binding_rule for cross-object data (refAt, CEL)
                - describe_variables before set_variable on existing objects
                
                ### Discovery tools
                
                - list_objects parentPath=… — children of folder
                - get_object path=… — single node metadata
                - search_objects query=… — find by name/path fragment
                - list_variables path=… — current variable values for dashboards/widgets
                """;
    }

    public static Map<String, Object> summary() {
        return Map.of(
                "deviceRoot", "root.platform.devices",
                "dashboardRoot", "root.platform.dashboards",
                "alertRulesRoot", AutomationTreeService.ALERT_RULES_ROOT,
                "correlatorsRoot", AutomationTreeService.CORRELATORS_ROOT,
                "modelCatalogs", List.of(
                        ModelCatalogRoots.RELATIVE,
                        ModelCatalogRoots.INSTANCE,
                        ModelCatalogRoots.ABSOLUTE
                ),
                "legacyRemoved", ModelCatalogRoots.LEGACY,
                "discoveryTools", List.of(
                        "list_objects", "get_object", "search_objects",
                        "list_variables", "list_object_models", "describe_variables"
                )
        );
    }
}
