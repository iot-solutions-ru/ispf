package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Seeds and recovers compact LITE plans for mutation tasks when the model fails to emit
 * {@code phase=plan} (plan-before-execute guard loops). Covers all reference scenarios and
 * assignment-type defaults — not SNMP-only.
 */
final class AgentLitePlanBootstrap {

    static final String PLANNING_GUARD_ERROR =
            "Execution finish blocked: produce a plan before mutating the platform.";

    private static final int PLANNING_GUARD_RECOVERY_THRESHOLD = 2;

    private AgentLitePlanBootstrap() {
    }

    static void seedDraftPlanIfNeeded(
            AgentRunState runState,
            String userMessage,
            boolean requireApprovalForMutate
    ) {
        if (!requireApprovalForMutate || runState == null) {
            return;
        }
        if (runState.planPhase() != AgentPlanPhase.PLANNING) {
            return;
        }
        if (!runState.storedPlan().isEmpty()) {
            return;
        }
        resolveDraftPlan(userMessage, runState.planDepth()).ifPresent(runState::setStoredPlan);
    }

    static Optional<Map<String, Object>> resolveFinishPlan(String userMessage, AgentRunState runState) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        AgentPlanDepth depth = runState != null ? runState.planDepth() : AgentPlanDepth.LITE;
        Map<String, Object> draft = resolveDraftPlan(userMessage, depth).orElse(null);
        if (draft == null || draft.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> finish = new LinkedHashMap<>();
        finish.put("phase", "plan");
        finish.put("interactive", true);
        finish.put("plan", new LinkedHashMap<>(draft));
        finish.put("planDepth", AgentPlanDepth.LITE.name());
        finish.put("suggestions", List.of(Map.of(
                "label", "Утвердить полный план",
                "message", "Утверждаю план, начинай выполнение",
                "primary", true
        )));
        if (runState != null && !runState.storedPlan().isEmpty()) {
            finish.put("plan", AgentPlanGuard.mergePlans(runState.storedPlan(), draft));
        }
        return Optional.of(finish);
    }

    static boolean shouldRecoverFromPlanningGuardLoop(List<Map<String, Object>> steps, String guardError) {
        if (!PLANNING_GUARD_ERROR.equals(guardError)) {
            return false;
        }
        return AgentPlatformTurnGuard.countRepeatedGuardError(steps, guardError) >= PLANNING_GUARD_RECOVERY_THRESHOLD;
    }

    static Optional<Map<String, Object>> resolveDraftPlan(String userMessage, AgentPlanDepth planDepth) {
        if (!eligible(userMessage, planDepth)) {
            return Optional.empty();
        }
        Optional<ReferenceScenarioCatalog.ReferenceScenario> scenario =
                ReferenceScenarioCatalog.matchBest(userMessage);
        if (scenario.isPresent()) {
            return Optional.of(planFromScenario(scenario.get()));
        }
        AgentAssignmentClassifier.Classification classification = AgentAssignmentClassifier.classify(userMessage);
        return Optional.of(planFromAssignmentType(classification.type(), userMessage));
    }

    private static boolean eligible(String userMessage, AgentPlanDepth planDepth) {
        if (userMessage == null || userMessage.isBlank() || planDepth == AgentPlanDepth.FULL) {
            return false;
        }
        if (ReferenceScenarioCatalog.matchBest(userMessage).isPresent()) {
            return true;
        }
        if (!AgentPlanGuard.impliesPlatformMutation(userMessage)) {
            return false;
        }
        AgentAssignmentClassifier.Classification classification = AgentAssignmentClassifier.classify(userMessage);
        if (classification.type() == AgentAssignmentType.EXPLORE_READONLY) {
            return false;
        }
        if (classification.fastPath()) {
            return true;
        }
        if (classification.type() == AgentAssignmentType.FOLLOW_UP) {
            return true;
        }
        return !classification.type().requiresFullSpecIntake()
                || !AgentPlanGuard.requiresPlanning(userMessage);
    }

    private static Map<String, Object> planFromScenario(ReferenceScenarioCatalog.ReferenceScenario scenario) {
        List<String> steps = enrichScenarioSteps(scenario);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("goal", scenario.planGoal());
        plan.put("steps", steps);
        plan.put("referenceScenarioId", scenario.id());
        return plan;
    }

    private static List<String> enrichScenarioSteps(ReferenceScenarioCatalog.ReferenceScenario scenario) {
        return switch (scenario.id()) {
            case "snmp-monitoring-lab" -> snmpLocalhostSteps();
            case "virtual-device-lab" -> virtualPumpSteps();
            case "pump-station-scada" -> pumpStationScadaSteps();
            case "workflow-hydro-impact" -> hydroWorkflowSteps();
            case "alert-automation" -> alertAutomationSteps();
            case "dashboard-monitoring" -> dashboardOverviewSteps();
            case "mes-bundle-deploy" -> mesBundleSteps();
            case "bundle-validate-dry-run" -> bundleValidateSteps();
            case "operator-report-readonly" -> reportSteps();
            case "tree-function-deploy" -> treeFunctionSteps();
            default -> numbered(scenario.planSteps());
        };
    }

    private static Map<String, Object> planFromAssignmentType(AgentAssignmentType type, String userMessage) {
        String text = userMessage.toLowerCase(Locale.ROOT);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("steps", switch (type) {
            case MONITORING_LAB -> monitoringLabSteps(text);
            case AUTOMATION_RULES -> automationSteps(text);
            case APPLICATION_BUNDLE -> applicationBundleSteps(text);
            case SCADA_HMI -> scadaHmiSteps(text);
            case REPORTING -> reportSteps();
            case INTEGRATION_SKELETON -> treeFunctionSteps();
            case INDUSTRIAL_FACILITY -> pumpStationScadaSteps();
            case FOLLOW_UP -> followUpSteps(text);
            default -> genericMutationSteps();
        });
        plan.put("goal", switch (type) {
            case MONITORING_LAB -> monitoringLabGoal(text);
            case AUTOMATION_RULES -> containsAny(text, "workflow", "bpmn")
                    ? "Workflow automation"
                    : "Alert and automation rules";
            case APPLICATION_BUNDLE -> containsAny(text, "validate", "провер", "dry")
                    ? "Validate application bundle"
                    : "Deploy application bundle";
            case SCADA_HMI -> containsAny(text, "mimic", "мимик", "мнемо", "scada")
                    ? "SCADA mimic and dashboard"
                    : "Monitoring dashboard";
            case REPORTING -> "Reports and KPI preview";
            case INTEGRATION_SKELETON -> "Integration skeleton and tree function";
            case INDUSTRIAL_FACILITY -> "Facility devices, mimic, and dashboard";
            case FOLLOW_UP -> "Extend existing platform objects";
            default -> "Platform changes per user request";
        });
        return plan;
    }

    private static String monitoringLabGoal(String text) {
        if (containsAny(text, "virtual", "насос", "pump", "lab-pump")) {
            return "Virtual lab device with telemetry";
        }
        return "SNMP monitoring per user request";
    }

    private static List<String> monitoringLabSteps(String text) {
        if (containsAny(text, "virtual", "насос", "pump", "lab-pump", "давлен")) {
            return virtualPumpSteps();
        }
        return snmpLocalhostSteps();
    }

    private static List<String> automationSteps(String text) {
        if (containsAny(text, "workflow", "bpmn", "гидроудар", "hydro")) {
            return hydroWorkflowSteps();
        }
        return alertAutomationSteps();
    }

    private static List<String> applicationBundleSteps(String text) {
        if (containsAny(text, "validate", "провер", "dry", "без деплоя")) {
            return bundleValidateSteps();
        }
        return mesBundleSteps();
    }

    private static List<String> scadaHmiSteps(String text) {
        if (containsAny(text, "mimic", "мимик", "мнемо", "scada", "насосн", "pump")) {
            return pumpStationScadaSteps();
        }
        return dashboardOverviewSteps();
    }

    private static List<String> followUpSteps(String text) {
        if (containsAny(text, "дашборд", "dashboard", "widget", "виджет", "график", "chart")) {
            return List.of(
                    "1. get_object / get_dashboard_layout — текущее состояние",
                    "2. list_variables — метрики для привязок",
                    "3. add_dashboard_widget или set_dashboard_layout — обновить layout",
                    "4. get_dashboard_layout — проверить widgetCount>0"
            );
        }
        if (containsAny(text, "device", "устройств", "driver", "драйвер")) {
            return List.of(
                    "1. get_object path=<device> — текущая конфигурация",
                    "2. list_variables path=<device> — теги",
                    "3. set_variable / configure_driver — изменить конфигурацию",
                    "4. driver_control start — перезапуск драйвера при необходимости"
            );
        }
        return genericMutationSteps();
    }

    private static List<String> snmpLocalhostSteps() {
        return List.of(
                "1. search_context query=snmp agent monitoring topic=drivers — templateId, driverConfigJson, mappings",
                "2. list_objects parentPath=root.platform.devices",
                "3. get_object path=<devicePath> — create_object с templateId из docs если отсутствует",
                "4. set_variable driverConfigJson / driverPointMappingsJson — значения из search_context",
                "5. configure_driver driverId=snmp autoStart=true",
                "6. list_variables path=<devicePath>",
                "7. get_object path=<dashboardPath> — create_object DASHBOARD + set_dashboard_layout template=snmp-host-monitoring"
        );
    }

    private static List<String> virtualPumpSteps() {
        return List.of(
                "1. list_virtual_profiles — OOTB virtual defaults (no profiles)",
                "2. list_objects parentPath=root.platform.devices",
                "3. create_virtual_device name=lab-pump-01",
                "4. configure_driver / driver_control start",
                "5. list_variables — проверить sineWave/temperature"
        );
    }


    private static List<String> pumpStationScadaSteps() {
        return List.of(
                "1. list_objects parentPath=root.platform.devices",
                "2. list_virtual_profiles — OOTB virtual; list_relative_blueprints for domain models",
                "3. create_virtual_device — устройства насосной станции",
                "4. save_mimic_diagram — мнемосхема с bindings",
                "5. create_object type=DASHBOARD + set_dashboard_layout template=scada-facility-overview",
                "6. configure_alert — пороговые алерты на ключевые теги"
        );
    }

    private static List<String> hydroWorkflowSteps() {
        return List.of(
                "1. get_automation_schema topic=workflow",
                "2. list_objects parentPath=root.platform.workflows",
                "3. create_object type=WORKFLOW name=hydro-impact",
                "4. save_workflow_bpmn path=<workflowPath>",
                "5. update_workflow_status ACTIVE + run_workflow dry-run"
        );
    }

    private static List<String> alertAutomationSteps() {
        return List.of(
                "1. list_objects parentPath=root.platform.devices",
                "2. list_variables path=<devicePath>",
                "3. configure_alert — правило на monitored variable",
                "4. list_events / fire_event — smoke проверка"
        );
    }

    private static List<String> dashboardOverviewSteps() {
        return List.of(
                "1. list_objects parentPath=root.platform.devices",
                "2. list_variables на целевых устройствах",
                "3. create_object type=DASHBOARD parentPath=root.platform.dashboards",
                "4. set_dashboard_layout template=monitoring-overview или scada-facility-overview",
                "5. get_dashboard_layout — widgetCount>0"
        );
    }

    private static List<String> mesBundleSteps() {
        return List.of(
                "1. search_context query=mes-reference bundle deploy",
                "2. validate_bundle appId=mes-reference",
                "3. dry_run_deploy appId=mes-reference",
                "4. import_package / register_application — deploy bundle",
                "5. list_functions objectPath=<devicePath> appId=mes-reference"
        );
    }

    private static List<String> bundleValidateSteps() {
        return List.of(
                "1. search_context query=application bundle validate",
                "2. validate_bundle appId=<bundle>",
                "3. dry_run_deploy appId=<bundle> — без import_package"
        );
    }

    private static List<String> reportSteps() {
        return List.of(
                "1. list_reports — доступные отчёты",
                "2. get_report_schema reportPath=<path>",
                "3. run_report preview — без изменений дерева",
                "4. get_variable_history — KPI для контекста"
        );
    }

    private static List<String> treeFunctionSteps() {
        return List.of(
                "1. list_objects parentPath=root.platform.applications",
                "2. get_function_template topic=java|script",
                "3. save_application_function / deploy_tree_function",
                "4. invoke_tree_function — smoke test"
        );
    }

    private static List<String> genericMutationSteps() {
        return List.of(
                "1. list_objects / search_context — ground truth",
                "2. list_variables / get_object — проверить существующие объекты",
                "3. create_object / set_variable — внести изменения",
                "4. list_variables / get_dashboard_layout — верификация"
        );
    }

    private static List<String> numbered(List<String> raw) {
        List<String> steps = new ArrayList<>();
        int index = 1;
        for (String step : raw) {
            if (step == null || step.isBlank()) {
                continue;
            }
            String trimmed = step.trim();
            if (trimmed.matches("^\\d+[.)]\\s+.*")) {
                steps.add(trimmed);
            } else {
                steps.add(index + ". " + trimmed);
            }
            index++;
        }
        return List.copyOf(steps);
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
