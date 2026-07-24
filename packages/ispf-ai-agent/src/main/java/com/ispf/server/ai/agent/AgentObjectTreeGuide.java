package com.ispf.server.ai.agent;

import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
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
                + "| "
                + com.ispf.server.query.ObjectQueryCatalog.QUERIES_ROOT
                + " | Queries | CUSTOM (function run, sourceType=object-query) |\n"
                + "| "
                + com.ispf.server.eventfilter.EventFilterObjectService.EVENT_FILTERS_ROOT
                + " | Event filters | EVENT_FILTER |\n"
                + """
                | root.platform.mixin-blueprints | Mixin model catalog | MODEL definitions; apply via apply_mixin_blueprint |
                | root.platform.instance-types | Instance type catalog | (definitions only) |
                | root.platform.singleton-blueprints | Singleton live hubs | blueprint + application logic on same node |
                | root.platform.instances | User object instances | devices, meters, … from Instance Types (not singleton hubs) |
                
                Legacy `root.platform.blueprints` removed — use typed catalogs above.
                
                ### Object types & key variables
                
                - **DEVICE**: driverId, driverConfigJson, driverPointMappingsJson; poll via configure_driver / driver_control — **I/O only**, never the logic hub
                - **DASHBOARD**: title, layout (JSON widgets[]), refreshIntervalMs — NOT a variable named widgets
                - **SINGLETON hub** (`root.platform.singleton-blueprints.*`): unique orchestrator — KPIs, read(...) aggregations; prefer ensure_singleton_instance
                - **INSTANCE twin**: repeatable digital-twin logic via instantiate_instance_type (may parent DEVICE children under devices tree)
                - **CUSTOM**: folder / object-query under queries — grouping only unless shaped as INSTANCE/SINGLETON
                - **ALERT**: targetObjectPath, watchVariable, conditionExpr (CEL), eventName — tool configure_alert
                - **CORRELATOR**: patternType COUNT|SEQUENCE|EVENT_CHAIN — tool configure_correlator
                - **WORKFLOW**: BPMN in bundle or platform tree
                - **REPORT**: SQL report definition path for report widget
                - **Object query** (under root.platform.queries): CUSTOM child with `run` function (`sourceType=object-query`, spec in `sourceBody`)
                - **EVENT_FILTER**: eventNamePattern, sourceObjectPathPattern, severity range, filterExpression
                
                Hard rule: logic/hub object must not be ObjectType.DEVICE. Cluster hub+DEVICE children under devices.* is allowed; path is implementation choice.
                
                ### Models & templates
                
                - list_object_blueprints — templateId / modelName; rows include BlueprintType (MIXIN|INSTANCE|SINGLETON)
                - list_mixin_blueprints — MIXINs (virtual-lab-v1, …)
                - list_instance_types — INSTANCE templates (snmp-agent-v1, … — see docs/en/blueprints.md)
                - list_instance_types — INSTANCE blueprints for instantiate_instance_type
                - list_singleton_blueprints — SINGLETON blueprints for ensure_singleton_instance
                - get_object_blueprint — variables, events, functions of a blueprint
                - instantiate_instance_type — create object from INSTANCE catalog entry
                - apply_mixin_blueprint — attach mixin to existing objectPath (preferred over empty DEVICE + manual vars)
                - ensure_singleton_instance — align/create object by SINGLETON model contract
                - Model catalogs: """
                + BlueprintCatalogRoots.MIXIN
                + ", "
                + BlueprintCatalogRoots.INSTANCE
                + ", "
                + BlueprintCatalogRoots.SINGLETON
                + """
                
                - create_variable + create_binding_rule for cross-object data (read, CEL)
                - describe_variables before set_variable on existing objects
                
                ### Discovery tools (ground truth — use before create/mutate)
                
                Paths, modelName, profile, variableName must come from tool results in the current turn.
                Playbooks and recipes show patterns only; never copy example paths literally.
                
                ### Object-type sweep (complex TZ)
                
                Call get_automation_schema topic=objectTypes. For each layer in TZ, list_objects on catalog root, \
                then create_object / configure_* / instantiate_instance_type / apply_mixin_blueprint as needed.
                Include plan.objectTypesCoverage[] — never skip DEVICE, SINGLETON hub, alerts, HMI layers without explicit N/A.
                
                ### Canonical order (every object type)
                
                1. `list_objects parent=<exact folder>` — e.g. root.platform.workflows (NOT parent=root for deep paths)
                2. `create_object parentPath=<folder> name=… type=…` — use path from step 1
                3. Configure/save on returned path — never before create_object succeeds
                
                | Type | Parent folder | After create_object |
                |------|---------------|---------------------|
                | WORKFLOW | root.platform.workflows | save_workflow_bpmn → update_workflow_status ACTIVE → run_workflow |
                | MIMIC | root.platform.mimics | save_mimic_diagram / add_mimic_elements |
                | DASHBOARD | root.platform.dashboards | set_dashboard_layout or add_dashboard_widget |
                | DEVICE | root.platform.devices | configure_driver, set_variable, list_variables |
                | ALERT | root.platform.alert-rules | configure_alert |
                | REPORT | root.platform.reports | configure_report |
                
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
                        BlueprintCatalogRoots.MIXIN,
                        BlueprintCatalogRoots.INSTANCE,
                        BlueprintCatalogRoots.SINGLETON
                ),
                "legacyRemoved", BlueprintCatalogRoots.LEGACY,
                "discoveryTools", List.of(
                        "list_objects", "get_object", "search_objects",
                        "list_variables", "list_object_blueprints", "describe_variables",
                        "list_instance_types", "instantiate_instance_type", "ensure_singleton_instance"
                )
        );
    }
}
