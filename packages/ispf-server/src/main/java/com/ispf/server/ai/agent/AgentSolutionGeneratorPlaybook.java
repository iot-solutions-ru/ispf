package com.ispf.server.ai.agent;

/**
 * Solution generator playbook (BL-180): natural-language factory/plant spec → object tree,
 * dashboards, and alerts without manual tree edits.
 */
public final class AgentSolutionGeneratorPlaybook {

    private AgentSolutionGeneratorPlaybook() {
    }

    public static String referenceText() {
        return """
                ## Solution generator (BL-180)
                
                Goal: from a factory/plant spec («опиши завод», production line, tank farm, HVAC site)
                deliver a working tree + operator dashboards + alert rules in one agent run (<15 min target).
                
                ### Preconditions
                1. Capture specBrief: entities (devices, zones, lines), signals, operator goals, naming policy
                2. Run gapMatrix — objectTypesCoverage must list DEVICE, DASHBOARD, ALERT (+ MIMIC/REPORT if TZ requires)
                3. Pick domain adapter when applicable: industrial_oil_gas, building_hvac, mes_reference
                4. Prefer bundle base (get_example_bundle) when a reference app matches ≥70% of spec
                
                ### Pipeline (strict order)
                1. **Intake** — search_context topic=all + get_automation_schema topic=objectTypes + list_objects parent=root.platform
                2. **Structure** — create_object CUSTOM folder from specBrief.entities[0].name (never hardcoded slugs)
                3. **Sources** — for each entity: create_virtual_device / create_object DEVICE + configure_driver + list_variables (mandatory per device)
                4. **Aggregation** — CUSTOM hub variables + create_binding_rule (read/CEL) when spec needs computed KPIs
                5. **Historian** — configure_variable_history on trend/chart variables (from list_variables only)
                6. **Dashboards** — create_object DASHBOARD → set_dashboard_layout (template or custom) binding list_variables paths
                7. **SCADA branch** — create_object MIMIC → save_mimic_diagram when P&ID/HMI graphics required
                8. **Alerts** — configure_alert on hub/device thresholds from specBrief alarms section
                9. **Correlators** — configure_correlator when spec requires event chains (alert → workflow/report)
                10. **Operator shell** — configure_operator_ui OR bundle operatorUi with dashboards + alarmBar
                11. **Validate** — list_automation + list_variables count>0 + get_dashboard_layout + invoke_bff smoke
                12. **finish** — summary with tree paths, dashboard paths, alert paths, operator URL (?mode=operator&app=...)
                
                ### Factory spec → artifact map
                | Spec element | Tree artifact | Tool |
                |--------------|---------------|------|
                | Production line / area | CUSTOM folder | create_object |
                | Pump, valve, meter, sensor | DEVICE under folder | create_virtual_device / create_object + configure_driver |
                | Overview screen | DASHBOARD | create_object + set_dashboard_layout |
                | P&ID / mimic | MIMIC | create_object + save_mimic_diagram |
                | High temp / pressure alarm | ALERT | configure_alert |
                | Shift report | REPORT | configure_report + operatorUi |
                | Operator HMI app | OPERATOR_APP manifest | configure_operator_ui / import_package operatorUi |
                
                ### Domain shortcuts
                - **Oil & gas / pump station:** virtualPumpStation playbook + scada-facility-overview template
                - **Building HVAC:** building-hvac-app bundle + zone comfort dashboards
                - **MES / dispatch:** mes-reference bundle + BFF functions; extend functions[] not ad-hoc tree
                - **Lab / training:** virtualClusterMonitoring or lab-training bundle
                
                ### Anti-patterns (never)
                - finish before list_variables on every shipped device
                - Invent variable or object paths — only paths from list_objects / create_object responses
                - Skip configure_alert when spec explicitly lists alarm thresholds
                - Single dashboard when spec lists multiple operator screens (overview + detail + reports)
                - Mix bundle import with partial manual tree without validate_bundle OK
                
                See also: projectBlueprintGuide, AgentDeployPlaybook, virtualPumpStation, mesReferenceLifecycle.
                """;
    }
}
