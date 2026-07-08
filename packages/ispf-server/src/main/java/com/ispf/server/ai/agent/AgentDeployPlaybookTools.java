package com.ispf.server.ai.agent;

import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.ai.validation.BundleValidationResult;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.BundleManifestJsonSupport;
import com.ispf.server.operator.OperatorAppUiService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BL-177: explicit agent tools for {@link AgentDeployPlaybook} pipeline steps.
 */
final class AgentDeployPlaybookTools {

    private AgentDeployPlaybookTools() {
    }

    static List<PlatformAgentTool> all(
            ObjectMapper objectMapper,
            AiToolRegistry aiToolRegistry,
            ApplicationBundleDeployService bundleDeployService,
            OperatorAppUiService operatorAppUiService
    ) {
        return List.of(
                getDeployPlaybookTool(),
                deployStepTool("deploy_step_discover", "discover", null, null, null, null),
                deployStepTool("deploy_step_blueprint", "blueprint", null, null, null, null),
                deployStepValidateTool(objectMapper, aiToolRegistry),
                deployStepDryRunTool(objectMapper, aiToolRegistry),
                deployStepImportTool(objectMapper, bundleDeployService),
                deployStepOperatorUiTool(operatorAppUiService),
                deployStepVerifyTool(),
                deployStepTool("deploy_step_automation", "automation", null, null, null, null),
                deployStepTool("deploy_step_finish", "finish", null, null, null, null)
        );
    }

    private static PlatformAgentTool getDeployPlaybookTool() {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_deploy_playbook";
            }

            @Override
            public String description() {
                return "End-to-end deploy playbook (BL-177): ordered steps, tools, and reference text. "
                        + "Optional arg: step (discover|blueprint|validate|dry_run|import|operator_ui|verify|automation|finish).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String stepId = optionalString(arguments, "step");
                if (stepId != null) {
                    AgentDeployPlaybook.DeployStep step = AgentDeployPlaybook.stepById(stepId);
                    if (step == null) {
                        return Map.of("status", "ERROR", "error", "Unknown step: " + stepId);
                    }
                    return Map.of(
                            "status", "OK",
                            "step", stepRow(step),
                            "completedSteps", context.runState().completedPlanSteps()
                    );
                }
                List<Map<String, Object>> steps = new ArrayList<>();
                for (AgentDeployPlaybook.DeployStep step : AgentDeployPlaybook.steps()) {
                    steps.add(stepRow(step));
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "OK");
                result.put("playbook", "AgentDeployPlaybook");
                result.put("stepCount", steps.size());
                result.put("steps", steps);
                result.put("reference", AgentDeployPlaybook.referenceText());
                result.put("completedSteps", context.runState().completedPlanSteps());
                return result;
            }
        };
    }

    private static PlatformAgentTool deployStepTool(
            String toolName,
            String stepId,
            ObjectMapper objectMapper,
            AiToolRegistry aiToolRegistry,
            ApplicationBundleDeployService bundleDeployService,
            OperatorAppUiService operatorAppUiService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return toolName;
            }

            @Override
            public String description() {
                AgentDeployPlaybook.DeployStep step = AgentDeployPlaybook.stepById(stepId);
                String title = step != null ? step.title() : stepId;
                return "Deploy playbook step: " + title + ". Returns guidance and marks step complete. "
                        + "Use get_deploy_playbook for full pipeline.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                return guidanceResult(stepId, context, null);
            }
        };
    }

    private static PlatformAgentTool deployStepValidateTool(ObjectMapper objectMapper, AiToolRegistry aiToolRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "deploy_step_validate";
            }

            @Override
            public String description() {
                return "Deploy playbook validate step — runs validate_bundle when appId+manifest provided; "
                        + "otherwise returns step guidance.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                Object manifestRaw = arguments.get("manifest");
                if (appId.isBlank() || !(manifestRaw instanceof Map<?, ?> manifestMap)) {
                    return guidanceResult("validate", context, "Provide appId and manifest to run validation");
                }
                var parsed = BundleManifestJsonSupport.parse(objectMapper, (Map<String, Object>) manifestMap);
                Map<String, Object> result = aiToolRegistry.validateBundle(appId, parsed, context.actor());
                if (BundleValidationResult.OK.equals(result.get("status"))) {
                    context.runState().markBundleValidated(appId);
                    context.runState().markCompletedPlanStep("deploy:validate");
                }
                result.put("playbookStep", "validate");
                return result;
            }
        };
    }

    private static PlatformAgentTool deployStepDryRunTool(ObjectMapper objectMapper, AiToolRegistry aiToolRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "deploy_step_dry_run";
            }

            @Override
            public String description() {
                return "Deploy playbook dry-run step — runs dry_run_deploy when appId+manifest provided.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                Object manifestRaw = arguments.get("manifest");
                if (appId.isBlank() || !(manifestRaw instanceof Map<?, ?> manifestMap)) {
                    return guidanceResult("dry_run", context, "Provide appId and manifest for dry-run");
                }
                var parsed = BundleManifestJsonSupport.parse(objectMapper, (Map<String, Object>) manifestMap);
                Map<String, Object> result = aiToolRegistry.dryRunDeploy(appId, parsed, context.actor());
                if (BundleValidationResult.OK.equals(result.get("status"))) {
                    context.runState().markBundleValidated(appId);
                    context.runState().markCompletedPlanStep("deploy:dry_run");
                }
                result.put("playbookStep", "dry_run");
                return result;
            }
        };
    }

    private static PlatformAgentTool deployStepImportTool(
            ObjectMapper objectMapper,
            ApplicationBundleDeployService bundleDeployService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "deploy_step_import";
            }

            @Override
            public String description() {
                return "Deploy playbook import step — runs import_package when appId+manifest provided "
                        + "(requires prior validate/dry-run OK in this run).";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                if (appId.isBlank()) {
                    appId = stringArg(arguments, "packageId");
                }
                Object manifestRaw = arguments.get("manifest");
                if (appId.isBlank() || !(manifestRaw instanceof Map<?, ?> manifestMap)) {
                    return guidanceResult("import", context, "Provide appId and manifest to import");
                }
                if (!context.runState().isBundleValidated(appId)) {
                    return Map.of(
                            "status", "ERROR",
                            "playbookStep", "import",
                            "error", "Run deploy_step_validate or deploy_step_dry_run with status OK first"
                    );
                }
                var parsed = BundleManifestJsonSupport.parse(objectMapper, (Map<String, Object>) manifestMap);
                Map<String, Object> result = bundleDeployService.deploy(appId, parsed);
                context.runState().markCompletedPlanStep("deploy:import");
                result.put("playbookStep", "import");
                result.put("status", "OK");
                return result;
            }
        };
    }

    private static PlatformAgentTool deployStepOperatorUiTool(OperatorAppUiService operatorAppUiService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "deploy_step_operator_ui";
            }

            @Override
            public String description() {
                return "Deploy playbook operator UI step — configure_operator_ui from manifest operatorUi "
                        + "or explicit title/defaultDashboard/dashboards[].";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                String title = stringArg(arguments, "title");
                String defaultDashboard = stringArg(arguments, "defaultDashboard");
                Object dashboardsRaw = arguments.get("dashboards");
                if (appId.isBlank() || title.isBlank() || defaultDashboard.isBlank()
                        || !(dashboardsRaw instanceof List<?> list) || list.isEmpty()) {
                    return guidanceResult("operator_ui", context,
                            "Provide appId, title, defaultDashboard, dashboards[] from operatorUi manifest");
                }
                List<Map<String, String>> dashboards = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> row) {
                        String path = String.valueOf(row.get("path"));
                        String dashTitle = row.containsKey("title") ? String.valueOf(row.get("title")) : path;
                        if (!path.isBlank()) {
                            dashboards.add(Map.of("path", path, "title", dashTitle));
                        }
                    }
                }
                if (dashboards.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "dashboards must contain at least one {path, title}");
                }
                try {
                    operatorAppUiService.getUi(appId);
                } catch (Exception ex) {
                    try {
                        operatorAppUiService.createApp(appId, title);
                    } catch (Exception createEx) {
                        return Map.of("status", "ERROR", "error", createEx.getMessage());
                    }
                }
                try {
                    Map<String, Object> saved = operatorAppUiService.saveUi(appId, title, defaultDashboard, dashboards);
                    context.runState().markCompletedPlanStep("deploy:operator_ui");
                    return Map.of("status", "OK", "playbookStep", "operator_ui", "appId", appId, "ui", saved);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool deployStepVerifyTool() {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "deploy_step_verify";
            }

            @Override
            public String description() {
                return "Deploy playbook verify step — checklist for post-deploy smoke (list_objects, BFF, reports). "
                        + "Optional appId for operator link hint.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                Map<String, Object> result = guidanceResult("verify", context, null);
                List<String> checks = List.of(
                        "list_objects parentPath=root.platform.applications",
                        "list_variables on device paths from bundle",
                        "invoke_bff on shipped functions",
                        "run_report when reports[] in manifest"
                );
                result.put("checks", checks);
                if (!appId.isBlank()) {
                    result.put("operatorUrlHint", "?mode=operator&app=" + appId);
                }
                context.runState().markCompletedPlanStep("deploy:verify");
                return result;
            }
        };
    }

    private static Map<String, Object> guidanceResult(String stepId, AgentContext context, String hint) {
        AgentDeployPlaybook.DeployStep step = AgentDeployPlaybook.stepById(stepId);
        if (step == null) {
            return Map.of("status", "ERROR", "error", "Unknown step: " + stepId);
        }
        context.runState().markCompletedPlanStep("deploy:" + step.id());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("playbookStep", step.id());
        result.put("step", stepRow(step));
        if (hint != null) {
            result.put("hint", hint);
        }
        result.put("nextStep", nextStepId(step.order()));
        return result;
    }

    private static String nextStepId(int currentOrder) {
        for (AgentDeployPlaybook.DeployStep step : AgentDeployPlaybook.steps()) {
            if (step.order() == currentOrder + 1) {
                return step.id();
            }
        }
        return "";
    }

    private static Map<String, Object> stepRow(AgentDeployPlaybook.DeployStep step) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", step.id());
        row.put("order", step.order());
        row.put("title", step.title());
        row.put("instruction", step.instruction());
        row.put("tools", step.tools());
        return row;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }
}
