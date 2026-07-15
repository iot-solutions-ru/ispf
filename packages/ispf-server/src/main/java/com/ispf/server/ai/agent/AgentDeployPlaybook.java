package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Locale;

/**
 * End-to-end agent deploy playbook (BL-177): spec → bundle → deploy → operator UI without manual edits.
 */
public final class AgentDeployPlaybook {

    public record DeployStep(
            String id,
            int order,
            String title,
            String instruction,
            List<String> tools
    ) {
    }

    private static final List<DeployStep> STEPS = List.of(
            new DeployStep(
                    "discover",
                    1,
                    "Discover platform state",
                    "list_objects, search_context, get_automation_schema topic=all",
                    List.of("list_objects", "search_context", "get_automation_schema")
            ),
            new DeployStep(
                    "blueprint",
                    2,
                    "Load reference bundle blueprint",
                    "get_example_bundle appId=<from spec> sections=[manifest,migrations,functions,operatorUi]",
                    List.of("get_example_bundle", "search_platform_recipes")
            ),
            new DeployStep(
                    "validate",
                    3,
                    "Validate bundle manifest",
                    "validate_bundle appId=... manifest={...} → status OK before any deploy",
                    List.of("validate_bundle", "deploy_step_validate")
            ),
            new DeployStep(
                    "dry_run",
                    4,
                    "Dry-run deploy",
                    "dry_run_deploy appId=... manifest={...} → review wouldApply paths",
                    List.of("dry_run_deploy", "deploy_step_dry_run")
            ),
            new DeployStep(
                    "import",
                    5,
                    "Import package",
                    "import_package appId=... manifest={...} after validate/dry-run OK",
                    List.of("import_package", "deploy_step_import")
            ),
            new DeployStep(
                    "operator_ui",
                    6,
                    "Configure operator shell",
                    "configure_operator_ui from manifest operatorUi (title, dashboards, reports, alarmBar)",
                    List.of("configure_operator_ui", "deploy_step_operator_ui")
            ),
            new DeployStep(
                    "verify",
                    7,
                    "Verify deployed solution",
                    "list_objects parentPath=root.platform; list_variables; invoke_bff smoke; run_report when shipped",
                    List.of("list_objects", "list_variables", "invoke_bff", "run_report", "deploy_step_verify")
            ),
            new DeployStep(
                    "automation",
                    8,
                    "Optional automation",
                    "configure_alert, configure_correlator, save_workflow_bpmn when TZ requires",
                    List.of("configure_alert", "configure_correlator", "save_workflow_bpmn")
            ),
            new DeployStep(
                    "finish",
                    9,
                    "Finish with operator link",
                    "summary with appId, operator URL (?mode=operator&app=...), verified tree paths only",
                    List.of("finish", "get_operator_link")
            )
    );

    private AgentDeployPlaybook() {
    }

    public static List<DeployStep> steps() {
        return STEPS;
    }

    public static DeployStep stepById(String stepId) {
        if (stepId == null || stepId.isBlank()) {
            return null;
        }
        String normalized = stepId.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (DeployStep step : STEPS) {
            if (step.id().equals(normalized)) {
                return step;
            }
        }
        return null;
    }

    public static String referenceText() {
        return """
                ## End-to-end agent deploy (BL-177)
                
                Goal: deliver a working operator-facing solution from a natural-language spec with **no human tree edits**.
                Every artifact must exist in the object tree or application bundle after finish.
                
                ### Preconditions
                1. specBrief captured (entities, signals, operator goals)
                2. gapMatrix reviewed — objectTypesCoverage lists DEVICE, DASHBOARD, REPORT, OPERATOR_APP as needed
                3. Domain adapter selected when applicable (industrial_oil_gas, building_hvac, mes_reference)
                
                ### Pipeline (strict order)
                Prefer **run_deploy_playbook appId=…** for one-shot BL-177 when a matching example bundle exists.
                Otherwise:
                1. **Discover** — list_objects, search_context, get_automation_schema topic=all
                2. **Blueprint** — get_example_bundle appId=<from spec or recipes> sections=[manifest,migrations,functions,operatorUi]
                3. **Validate** — validate_bundle appId=... manifest={...} → status OK (fix errors before proceed)
                4. **Dry run** — dry_run_deploy appId=... manifest={...} → review wouldApply paths
                5. **Deploy** — import_package appId=... manifest={...}
                6. **Operator shell** — configure_operator_ui from manifest operatorUi (title, dashboards, reports, alarmBar)
                7. **SCADA/MES verify** — list_objects parentPath=root.platform; list_variables on device paths;
                   invoke_bff / invoke_tree_function for BFF smoke; run_report when reports shipped
                8. **Automation** (if TZ requires) — configure_alert, configure_correlator, save_workflow_bpmn
                9. **finish** — summary with appId, operator URL (?mode=operator&app=...), key tree paths
                
                ### SCADA branch
                - create_object MIMIC + save_mimic_diagram when P&ID/HMI graphics required
                - set_dashboard_layout template=scada-facility-overview or custom layout referencing mimicPath
                - configure_variable_history on trend variables before dashboard charts
                
                ### MES branch
                - Prefer mes-reference / mes-oee-reference bundle as base; extend functions[] not ad-hoc tree edits
                - OEE / work-order screens via operatorUi.dashboards + BFF functions from bundle
                
                ### HVAC branch
                - building-hvac-app bundle pattern: migrations + hvac_listZones BFF + operatorUi
                - Zone comfort dashboards bind to SQL-backed functions or device variables
                
                ### Anti-patterns (never)
                - import_package without prior validate_bundle OK in the same run
                - finish with invented paths — only paths returned by list_objects / deploy tools
                - "configure in UI" deferrals when configure_operator_ui / set_dashboard_layout exist
                - Partial deploy: migrations applied but operatorUi missing when spec requires operator mode
                
                See also: applicationLifecycleGuide, mesReferenceLifecycle, virtualClusterMonitoring, scadaMimicGuide.
                """;
    }
}
