package com.ispf.server.ai.agent;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.expression.BindingExpressionValidator;
import com.ispf.expression.FormalVerificationReport;
import com.ispf.server.alert.AlertRule;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.correlator.CorrelatorActionType;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.correlator.EventCorrelator;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.operator.OperatorAppUiService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FW-48: automation, bindings, and operator UI tools for end-to-end project builds.
 */
final class AgentAutomationTools {

    private AgentAutomationTools() {
    }

    static List<PlatformAgentTool> all(
            AutomationTreeService automationTreeService,
            OperatorAppUiService operatorAppUiService,
            ObjectTreePort objectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            BindingRulesService bindingRulesService,
            BindingDependencyIndex bindingDependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            AgentRecipeCatalog recipeCatalog,
            ObjectMapper objectMapper,
            ExpressionFormalVerificationService formalVerificationService
    ) {
        return List.of(
                configureAlertTool(
                        automationTreeService,
                        objectTreePort,
                        objectAccessService,
                        tenantScopeService,
                        formalVerificationService
                ),
                configureCorrelatorTool(automationTreeService, objectTreePort, objectAccessService, tenantScopeService),
                listAutomationTool(automationTreeService, objectAccessService, tenantScopeService),
                getAutomationSchemaTool(recipeCatalog),
                searchPlatformRecipesTool(recipeCatalog),
                createVariableTool(objectTreePort, objectAccessService, tenantScopeService, objectMapper),
                createBindingRuleTool(
                        objectTreePort,
                        objectAccessService,
                        tenantScopeService,
                        bindingRulesService,
                        bindingDependencyIndex,
                        bindingRuleEngine,
                        formalVerificationService
                ),
                verifyCelConditionTool(formalVerificationService),
                configureVariableHistoryTool(objectTreePort, objectAccessService, tenantScopeService),
                configureOperatorUiTool(operatorAppUiService)
        );
    }

    private static PlatformAgentTool verifyCelConditionTool(ExpressionFormalVerificationService formalVerificationService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "verify_cel_condition";
            }

            @Override
            public String description() {
                return "Formally verify a boolean CEL condition (Z3). Args: expression (required), "
                        + "optional equivalentTo (second expression for equivalence proof). "
                        + "Returns verification{status,codes,findings,satisfiable,alwaysTrue,equivalent}. "
                        + "Use before configure_alert / create_binding_rule to catch dead or tautology conditions.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String expression = stringArg(arguments, "expression");
                if (expression.isBlank()) {
                    return Map.of("status", "ERROR", "error", "expression is required");
                }
                String equivalentTo = optionalString(arguments, "equivalentTo");
                FormalVerificationReport report = equivalentTo != null && !equivalentTo.isBlank()
                        ? formalVerificationService.verifyEquivalence(expression, equivalentTo)
                        : formalVerificationService.analyze(expression);
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("status", report.blocksConditionApply() ? "REJECTED" : "OK");
                ok.put("ok", !report.blocksConditionApply());
                ok.put("verification", report);
                return ok;
            }
        };
    }

    private static PlatformAgentTool configureAlertTool(
            AutomationTreeService automationTreeService,
            ObjectTreePort objectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ExpressionFormalVerificationService formalVerificationService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_alert";
            }

            @Override
            public String description() {
                return "Create or update ALERT rule under root.platform.alert-rules. "
                        + "Args: path (existing) OR name (creates under alert-rules), targetObjectPath, watchVariable, "
                        + "conditionExpr (CEL, e.g. self.temperature[\"value\"] > 85), eventName, "
                        + "payloadVariable?, enabled?, edgeTrigger?, delaySeconds?, sustainWhileTrue?. "
                        + "Condition evaluates on targetObjectPath; eventName is auto-created and fired on the ALERT rule node. "
                        + "Operator alarmBar objectPathPrefix should be root.platform.alert-rules (or rule path). "
                        + "CEL conditions are formally verified (unsatisfiable/tautology rejected). "
                        + "Use get_automation_schema for field names.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                var auth = context.authentication();
                try {
                    String path = stringArg(arguments, "path");
                    String name = stringArg(arguments, "name");
                    if (path.isBlank() && name.isBlank()) {
                        return Map.of("status", "ERROR", "error", "path or name is required");
                    }
                    automationTreeService.ensurePlatformFolders();
                    if (path.isBlank()) {
                        path = AutomationTreeService.rulePathForName(name);
                    }
                    if (objectTreePort.tree().findByPath(path).isEmpty()
                            && path.startsWith(AutomationTreeService.ALERT_RULES_ROOT + ".")) {
                        objectAccessService.requireWrite(AutomationTreeService.ALERT_RULES_ROOT, auth);
                        String nodeName = path.substring(AutomationTreeService.ALERT_RULES_ROOT.length() + 1);
                        String display = !name.isBlank() ? name : nodeName;
                        objectTreePort.create(
                                AutomationTreeService.ALERT_RULES_ROOT,
                                nodeName,
                                ObjectType.ALERT,
                                display,
                                "",
                                null
                        );
                        automationTreeService.ensureAlertRuleStructure(path);
                    }
                    if (!tenantScopeService.isPathVisible(path, auth)) {
                        return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                    }
                    objectAccessService.requireWrite(path, auth);
                    automationTreeService.ensureAlertRuleStructure(path);

                    String targetObjectPath = optionalString(arguments, "targetObjectPath");
                    String watchVariable = optionalString(arguments, "watchVariable");
                    String conditionExpr = optionalString(arguments, "conditionExpr");
                    String eventName = optionalString(arguments, "eventName");
                    String payloadVariable = optionalString(arguments, "payloadVariable");
                    Boolean enabled = boolArg(arguments, "enabled", null);
                    Boolean edgeTrigger = boolArg(arguments, "edgeTrigger", null);
                    Integer delaySeconds = intArg(arguments, "delaySeconds", null);
                    Boolean sustainWhileTrue = boolArg(arguments, "sustainWhileTrue", null);
                    String priority = optionalString(arguments, "priority");
                    Boolean ackRequired = boolArg(arguments, "ackRequired", null);

                    if (targetObjectPath == null || watchVariable == null || conditionExpr == null || eventName == null) {
                        AlertRule current = automationTreeService.getAlertRule(path);
                        return Map.of(
                                "status", "OK",
                                "path", path,
                                "alert", alertPreview(current),
                                "hint", "Provide targetObjectPath, watchVariable, conditionExpr, eventName to update"
                        );
                    }

                    FormalVerificationReport verification =
                            formalVerificationService.requireSafeConditionForApply(conditionExpr);

                    String displayName = name.isBlank()
                            ? objectTreePort.require(path).displayName()
                            : name;
                    AlertRule updated = automationTreeService.updateAlertRule(
                            path,
                            displayName,
                            targetObjectPath,
                            watchVariable,
                            conditionExpr,
                            eventName,
                            payloadVariable != null ? payloadVariable : "",
                            enabled != null ? enabled : true,
                            edgeTrigger != null ? edgeTrigger : true,
                            delaySeconds != null ? delaySeconds : 0,
                            sustainWhileTrue != null ? sustainWhileTrue : false,
                            priority,
                            ackRequired,
                            null,
                            null,
                            null,
                            null,
                            optionalString(arguments, "deactivateExpr"),
                            intArg(arguments, "deactivateDelaySeconds", null),
                            intArg(arguments, "pollIntervalMs", null),
                            optionalString(arguments, "triggerMessage"),
                            optionalString(arguments, "clearEventName")
                    );
                    Map<String, Object> ok = new LinkedHashMap<>();
                    ok.put("status", "OK");
                    ok.put("path", path);
                    ok.put("alert", alertPreview(updated));
                    ok.put("verification", verification);
                    return ok;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool configureCorrelatorTool(
            AutomationTreeService automationTreeService,
            ObjectTreePort objectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_correlator";
            }

            @Override
            public String description() {
                return "Create or update CORRELATOR under root.platform.correlators. "
                        + "Args: path OR name, targetObjectPath, patternType (COUNT|SEQUENCE|EVENT_CHAIN|...), "
                        + "eventName, secondEventName?, windowSeconds?, minOccurrences?, cooldownSeconds?, "
                        + "actionType (FIRE_EVENT|START_WORKFLOW|...), actionTarget?, payloadFilterExpr?, enabled?. "
                        + "Use get_automation_schema for enums and fields.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                var auth = context.authentication();
                try {
                    String path = stringArg(arguments, "path");
                    String name = stringArg(arguments, "name");
                    if (path.isBlank() && name.isBlank()) {
                        return Map.of("status", "ERROR", "error", "path or name is required");
                    }
                    if (path.isBlank()) {
                        automationTreeService.ensurePlatformFolders();
                        path = AutomationTreeService.correlatorPathForName(name);
                        if (objectTreePort.tree().findByPath(path).isEmpty()) {
                            objectAccessService.requireWrite(AutomationTreeService.CORRELATORS_ROOT, auth);
                            objectTreePort.create(
                                    AutomationTreeService.CORRELATORS_ROOT,
                                    AutomationTreeService.slugify(name),
                                    ObjectType.CORRELATOR,
                                    name,
                                    "",
                                    null
                            );
                            automationTreeService.ensureCorrelatorStructure(path);
                        }
                    }
                    if (!tenantScopeService.isPathVisible(path, auth)) {
                        return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                    }
                    objectAccessService.requireWrite(path, auth);
                    automationTreeService.ensureCorrelatorStructure(path);

                    String targetObjectPath = optionalString(arguments, "targetObjectPath");
                    String patternTypeRaw = optionalString(arguments, "patternType");
                    String eventName = optionalString(arguments, "eventName");
                    if (targetObjectPath == null || patternTypeRaw == null || eventName == null) {
                        EventCorrelator current = automationTreeService.getCorrelator(path);
                        return Map.of(
                                "status", "OK",
                                "path", path,
                                "correlator", correlatorPreview(current),
                                "hint", "Provide targetObjectPath, patternType, eventName to update"
                        );
                    }

                    CorrelatorPatternType patternType = CorrelatorPatternType.valueOf(
                            patternTypeRaw.trim().toUpperCase(Locale.ROOT)
                    );
                    String secondEventName = optionalString(arguments, "secondEventName");
                    int windowSeconds = intArg(arguments, "windowSeconds", 60);
                    int minOccurrences = intArg(arguments, "minOccurrences", 3);
                    int cooldownSeconds = intArg(arguments, "cooldownSeconds", 30);
                    int sequenceGapSeconds = intArg(arguments, "sequenceGapSeconds", 0);
                    String actionTypeRaw = stringArg(arguments, "actionType");
                    if (actionTypeRaw.isBlank()) {
                        actionTypeRaw = "FIRE_EVENT";
                    }
                    CorrelatorActionType actionType = CorrelatorActionType.valueOf(
                            actionTypeRaw.trim().toUpperCase(Locale.ROOT)
                    );
                    String actionTarget = optionalString(arguments, "actionTarget");
                    String payloadFilterExpr = optionalString(arguments, "payloadFilterExpr");
                    Boolean enabled = boolArg(arguments, "enabled", null);

                    String displayName = name.isBlank()
                            ? objectTreePort.require(path).displayName()
                            : name;
                    EventCorrelator updated = automationTreeService.updateCorrelator(
                            path,
                            displayName,
                            targetObjectPath,
                            patternType,
                            eventName,
                            secondEventName,
                            windowSeconds,
                            minOccurrences,
                            cooldownSeconds,
                            sequenceGapSeconds,
                            actionType,
                            actionTarget != null ? actionTarget : "",
                            payloadFilterExpr != null ? payloadFilterExpr : "",
                            enabled != null ? enabled : true
                    );
                    return Map.of("status", "OK", "path", path, "correlator", correlatorPreview(updated));
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool listAutomationTool(
            AutomationTreeService automationTreeService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_automation";
            }

            @Override
            public String description() {
                return "List alert rules and event correlators on the platform. No args.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                var auth = context.authentication();
                List<Map<String, Object>> alerts = new ArrayList<>();
                for (AlertRule rule : automationTreeService.listAlertRules()) {
                    if (!tenantScopeService.isPathVisible(rule.id(), auth)) {
                        continue;
                    }
                    if (!objectAccessService.canRead(rule.id(), auth)) {
                        continue;
                    }
                    alerts.add(alertPreview(rule));
                }
                List<Map<String, Object>> correlators = new ArrayList<>();
                for (EventCorrelator correlator : automationTreeService.listCorrelators()) {
                    if (!tenantScopeService.isPathVisible(correlator.id(), auth)) {
                        continue;
                    }
                    if (!objectAccessService.canRead(correlator.id(), auth)) {
                        continue;
                    }
                    correlators.add(correlatorPreview(correlator));
                }
                return Map.of(
                        "status", "OK",
                        "alertRulesRoot", AutomationTreeService.ALERT_RULES_ROOT,
                        "correlatorsRoot", AutomationTreeService.CORRELATORS_ROOT,
                        "alertCount", alerts.size(),
                        "correlatorCount", correlators.size(),
                        "alerts", alerts,
                        "correlators", correlators
                );
            }
        };
    }

    private static PlatformAgentTool searchPlatformRecipesTool(AgentRecipeCatalog recipeCatalog) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "search_platform_recipes";
            }

            @Override
            public String description() {
                return "Search generated platform recipes. Args: optional query, category, industry, archetype, "
                        + "limit (default 25), offset (default 0).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                return recipeCatalog.search(
                        optionalString(arguments, "query"),
                        optionalString(arguments, "category"),
                        optionalString(arguments, "industry"),
                        optionalString(arguments, "archetype"),
                        intArg(arguments, "limit", 25),
                        intArg(arguments, "offset", 0)
                );
            }
        };
    }

    private static PlatformAgentTool getAutomationSchemaTool(AgentRecipeCatalog recipeCatalog) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_automation_schema";
            }

            @Override
            public String description() {
                return "Reference for ALERT/CORRELATOR variables, dashboard templates, widgets, object tree, bindings, "
                        + "operator UI, reports, workflows, SCADA, application lifecycle, schedules, platform rules. "
                        + "Optional arg: topic (alert|correlator|dashboard|widget|object|binding|operator|report|"
                        + "workflow|scada|lifecycle|schedule|platform-rule|projectBlueprint|groundTruth|instanceTypes|"
                        + "objectTypes|mixinBlueprints|recipes|projects|recipe/{id}|all).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String topic = stringArg(arguments, "topic");
                if (topic.isBlank()) {
                    topic = "all";
                }
                final String resolvedTopic = topic;
                String topicLower = topic.toLowerCase(Locale.ROOT);
                int offset = intArg(arguments, "offset", 0);
                int limit = intArg(arguments, "limit", 50);
                if (topicLower.startsWith("recipe/")) {
                    String recipeId = topic.substring("recipe/".length()).trim();
                    if (recipeId.isBlank()) {
                        Map<String, Object> err = new LinkedHashMap<>(AgentToolErrors.error(
                                "TOOL_ERROR",
                                "recipe/{id} requires non-empty id",
                                "",
                                ""
                        ));
                        err.put("topic", topic);
                        return err;
                    }
                    return recipeCatalog.findById(recipeId)
                            .<Map<String, Object>>map(recipe -> Map.of(
                                    "status", "OK",
                                    "topic", resolvedTopic,
                                    "recipe", AgentRecipeCatalog.toDetailRow(recipe)
                            ))
                            .orElseGet(() -> {
                                Map<String, Object> err = new LinkedHashMap<>(AgentToolErrors.error(
                                        "TOOL_ERROR",
                                        "Recipe not found: " + recipeId,
                                        "",
                                        ""
                                ));
                                err.put("topic", resolvedTopic);
                                return err;
                            });
                }
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("status", "OK");
                schema.put("topic", resolvedTopic);
                switch (topicLower) {
                    case "alert" -> schema.put("alert", alertSchema());
                    case "correlator" -> schema.put("correlator", correlatorSchema());
                    case "dashboard" -> schema.put("dashboard", dashboardSchema());
                    case "widget", "widgets" -> schema.put("widgets", AgentWidgetCatalog.catalogResponse("", ""));
                    case "object", "objects" -> schema.put("objects", AgentObjectTreeGuide.summary());
                    case "projectblueprint", "project-blueprint", "blueprint" -> {
                        schema.put("projectBlueprint", AgentPlaybooks.projectBlueprintGuide());
                        schema.put("planSections", AgentPlanSections.guide());
                    }
                    case "groundtruth", "ground-truth", "discovery" ->
                            schema.put("groundTruth", AgentPlaybooks.groundTruthGuide());
                    case "instancetypes", "instance-types", "instance-type", "instance" ->
                            schema.put("instanceTypes", AgentPlaybooks.instanceTypesGuide());
                    case "objecttypes", "object-types", "objecttype", "object-type" -> {
                        schema.put("objectTypes", AgentPlaybooks.objectTypesMatrixGuide());
                        schema.put("objectTypeCoverage", AgentPlaybooks.objectTypeCoverageGuide());
                        schema.put("objectTypeTable", objectTypeGuide());
                    }
                    case "mixinmodels", "mixin-models", "mixin-model", "mixinblueprints", "mixin-blueprints",
                         "relativemodels", "relative-models", "relative-model" ->
                            schema.put("mixinBlueprints", AgentPlaybooks.mixinBlueprintsGuide());
                    case "binding" -> schema.put("binding", bindingSchema());
                    case "operator" -> schema.put("operator", operatorSchema());
                    case "report", "reports" -> schema.put("reports", AgentPlaybooks.reportsGuide());
                    case "workflow", "workflows" -> schema.put("workflows", AgentPlaybooks.workflowGuide());
                    case "scada", "mimic", "mimics" -> schema.put("scada", AgentPlaybooks.scadaMimicGuide());
                    case "lifecycle", "application" -> schema.put("lifecycle", AgentPlaybooks.applicationLifecycleGuide());
                    case "schedule", "schedules" -> schema.put("schedules", AgentPlaybooks.scheduleGuide());
                    case "function", "functions" -> schema.put("functions", AgentPlaybooks.functionsGuide());
                    case "platform-rule", "platform-rules", "context" -> schema.put("platformRules", AgentPlaybooks.platformRuleGuide());
                    case "recipes" -> schema.put("recipes", recipeCatalog.indexSummary(offset, limit));
                    case "projects" -> schema.put(
                            "projects",
                            recipeCatalog.listProjects(
                                    optionalString(arguments, "industry"),
                                    optionalString(arguments, "archetype"),
                                    offset,
                                    limit
                            )
                    );
                    default -> {
                        schema.put("alert", alertSchema());
                        schema.put("correlator", correlatorSchema());
                        schema.put("dashboard", dashboardSchema());
                        schema.put("widgets", AgentWidgetCatalog.catalogSummary());
                        schema.put("objects", AgentObjectTreeGuide.summary());
                        schema.put("binding", bindingSchema());
                        schema.put("operator", operatorSchema());
                        schema.put("reports", AgentPlaybooks.reportsGuide());
                        schema.put("workflows", AgentPlaybooks.workflowGuide());
                        schema.put("scada", AgentPlaybooks.scadaMimicGuide());
                        schema.put("lifecycle", AgentPlaybooks.applicationLifecycleGuide());
                        schema.put("schedules", AgentPlaybooks.scheduleGuide());
                        schema.put("functions", AgentPlaybooks.functionsGuide());
                        schema.put("platformRules", AgentPlaybooks.platformRuleGuide());
                        schema.put("platformMaster", AgentPlaybooks.platformMasterGuide());
                        schema.put("projectBlueprint", AgentPlaybooks.projectBlueprintGuide());
                        schema.put("planSections", AgentPlanSections.guide());
                        schema.put("groundTruth", AgentPlaybooks.groundTruthGuide());
                        schema.put("instanceTypes", AgentPlaybooks.instanceTypesGuide());
                        schema.put("mixinBlueprints", AgentPlaybooks.mixinBlueprintsGuide());
                        schema.put("objectTypes", AgentPlaybooks.objectTypesMatrixGuide());
                        schema.put("objectTypeTable", objectTypeGuide());
                        schema.put("recipes", recipeCatalog.indexSummary(offset, limit));
                        schema.put(
                                "projects",
                                recipeCatalog.listProjects(
                                        optionalString(arguments, "industry"),
                                        optionalString(arguments, "archetype"),
                                        offset,
                                        limit
                                )
                        );
                    }
                }
                return schema;
            }
        };
    }

    private static PlatformAgentTool createVariableTool(
            ObjectTreePort objectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "create_variable";
            }

            @Override
            public String description() {
                return "Create a new variable on an object (prefer SINGLETON orchestrator or INSTANCE twin for "
                        + "app/twin logic; DEVICE is telemetry only). "
                        + "Args: path, name, valueType (DOUBLE|BOOLEAN|STRING|INTEGER), "
                        + "initialValue? (map or scalar), "
                        + "writable? (default false), historyEnabled? (default false). "
                        + "Use create_binding_rule for computed values; set_variable only updates writable vars.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String name = stringArg(arguments, "name");
                String valueType = stringArg(arguments, "valueType");
                if (path.isBlank() || name.isBlank() || valueType.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path, name, valueType are required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    PlatformObject node = objectTreePort.require(path);
                    if (node.getVariable(name).isPresent()) {
                        Map<String, Object> reused = new LinkedHashMap<>();
                        reused.put("status", "OK");
                        reused.put("reused", true);
                        reused.put("path", path);
                        reused.put("variable", Map.of("name", name));
                        reused.put(
                                "hint",
                                "Variable already exists — reuse it. For computed sums use create_binding_rule "
                                        + "expression=read(\"self/<a>\")+read(\"self/<b>\") targetVariable=" + name
                                        + ". Do not recreate."
                        );
                        return reused;
                    }
                    DataSchema schema = schemaForValueType(valueType);
                    boolean writable = boolArg(arguments, "writable", false);
                    boolean historyEnabled = boolArg(arguments, "historyEnabled", false);
                    DataRecord initialValue = null;
                    Object rawInitial = arguments.get("initialValue");
                    if (rawInitial == null) {
                        rawInitial = arguments.get("value");
                    }
                    if (rawInitial != null) {
                        initialValue = toInitialRecord(objectMapper, schema, rawInitial);
                    }
                    Variable created = objectTreePort.createVariable(
                            path,
                            name,
                            schema,
                            true,
                            writable,
                            initialValue,
                            historyEnabled,
                            null
                    );
                    Map<String, Object> preview = new LinkedHashMap<>();
                    preview.put("name", created.name());
                    preview.put("writable", created.writable());
                    return Map.of("status", "OK", "path", path, "variable", preview);
                } catch (Exception ex) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("status", "ERROR");
                    err.put("error", ex.getMessage() == null ? "create_variable failed" : ex.getMessage());
                    String msg = String.valueOf(err.get("error")).toLowerCase(Locale.ROOT);
                    if (msg.contains("must be double") || msg.contains("must be long") || msg.contains("must be int")) {
                        err.put(
                                "hint",
                                "For DOUBLE use initialValue=0 or 0.0 (number), or omit initialValue. "
                                        + "Do NOT send boolean/object/array. Example: "
                                        + "path=... name=waveSum valueType=DOUBLE writable=false — then create_binding_rule."
                        );
                    }
                    return err;
                }
            }
        };
    }

    private static PlatformAgentTool createBindingRuleTool(
            ObjectTreePort objectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            BindingRulesService bindingRulesService,
            BindingDependencyIndex bindingDependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            ExpressionFormalVerificationService formalVerificationService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "create_binding_rule";
            }

            @Override
            public String description() {
                return "Create or replace a binding rule on an object. Args: path, id, expression, "
                        + "targetVariable (for kind=variable, local target), targetRef (slash ref for cross-object variable/event), "
                        + "optional targetKind (variable|action|context|event), "
                        + "optional ruleKind (reactive|historian, default reactive), "
                        + "windowBucket (required for historian, e.g. 1m/5m), "
                        + "periodicMs (historian refresh, default 60000), "
                        + "contextPath (context target), eventName (event target), condition (CEL), "
                        + "remoteObjectPath + remoteVariableName (cross-object activator), "
                        + "onContextChange (bool, dashboard rules), onStartup, order. "
                        + "Historian rolling avg example: ruleKind=historian windowBucket=1m "
                        + "expression=avg(root.platform.devices.dev/sineWave, 1m) targetVariable=avg1m. "
                        + "Do NOT use rolling-avg Mixin Blueprints for 1-minute averages — use historian avg(). "
                        + "For dashboard @dashboardContext rules prefer configure_platform_context_rule.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String id = stringArg(arguments, "id");
                String targetVariable = stringArg(arguments, "targetVariable");
                String targetRef = stringArg(arguments, "targetRef");
                String expression = stringArg(arguments, "expression");
                String targetKind = com.ispf.core.binding.BindingTargetKind.normalize(stringArg(arguments, "targetKind"));
                BindingRuleKind ruleKind = parseRuleKind(arguments);
                if (path.isBlank() || id.isBlank() || expression.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path, id, expression are required");
                }
                if (com.ispf.core.binding.BindingTargetKind.VARIABLE.equals(targetKind)
                        && targetVariable.isBlank()
                        && targetRef.isBlank()) {
                    return Map.of("status", "ERROR", "error", "targetVariable or targetRef is required for targetKind=variable");
                }
                if (com.ispf.core.binding.BindingTargetKind.EVENT.equals(targetKind)
                        && stringArg(arguments, "eventName").isBlank()
                        && targetRef.isBlank()) {
                    return Map.of("status", "ERROR", "error", "eventName or targetRef is required for targetKind=event");
                }
                if (ruleKind == BindingRuleKind.HISTORIAN && targetVariable.isBlank() && targetRef.isBlank()) {
                    return Map.of("status", "ERROR", "error", "historian rules require targetVariable (or targetRef)");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    PlatformObject bindingHost = objectTreePort.require(path);
                    Map<String, Object> resolvedArgs = coerceEventBindingArgs(arguments, bindingHost);
                    targetRef = stringArg(resolvedArgs, "targetRef");
                    targetVariable = stringArg(resolvedArgs, "targetVariable");
                    String createdTargetVariable = "";
                    if (com.ispf.core.binding.BindingTargetKind.VARIABLE.equals(targetKind)
                            && !targetVariable.isBlank()
                            && targetRef.isBlank()
                            && bindingHost.getVariable(targetVariable).isEmpty()) {
                        // Course agents invent sum targets before create_variable — create DOUBLE sink.
                        objectTreePort.createVariable(
                                path,
                                targetVariable,
                                DataSchema.builder(targetVariable).field("value", FieldType.DOUBLE).build(),
                                true,
                                false,
                                null,
                                false,
                                null
                        );
                        createdTargetVariable = targetVariable;
                        bindingHost = objectTreePort.require(path);
                    }
                    // Historian helpers (avg/min/max/…) are not plain CEL — skip reactive CEL compile.
                    if (ruleKind != BindingRuleKind.HISTORIAN) {
                        BindingExpressionValidator.validateOrThrow(expression);
                    }
                    String condition = optionalString(resolvedArgs, "condition");
                    FormalVerificationReport conditionVerification = null;
                    if (condition != null && !condition.isBlank()) {
                        conditionVerification = formalVerificationService.requireSafeConditionForApply(condition);
                    }
                    BindingActivators activators = buildActivators(resolvedArgs, path, ruleKind);
                    com.ispf.core.binding.BindingTarget target = buildTarget(resolvedArgs, targetKind, targetVariable);
                    String windowBucket = optionalString(resolvedArgs, "windowBucket");
                    if (ruleKind == BindingRuleKind.HISTORIAN && (windowBucket == null || windowBucket.isBlank())) {
                        windowBucket = "1m";
                    }
                    BindingRule rule = new BindingRule(
                            id,
                            optionalString(resolvedArgs, "name"),
                            boolArg(resolvedArgs, "enabled", true),
                            intArg(resolvedArgs, "order", 0),
                            ruleKind,
                            activators,
                            condition != null ? condition : "",
                            expression,
                            target,
                            windowBucket,
                            null
                    );
                    BindingRule saved = bindingRulesService.upsertRule(path, rule);
                    bindingDependencyIndex.rebuild(path);
                    bindingRuleEngine.runRulesForObject(path);
                    Map<String, Object> ok = new LinkedHashMap<>();
                    ok.put("status", "OK");
                    ok.put("path", path);
                    ok.put("ruleId", saved.id());
                    ok.put("kind", saved.kind().name());
                    ok.put("target", saved.target().variableName());
                    if (saved.windowBucket() != null) {
                        ok.put("windowBucket", saved.windowBucket());
                    }
                    if (conditionVerification != null) {
                        ok.put("conditionVerification", conditionVerification);
                    }
                    if (!createdTargetVariable.isBlank()) {
                        ok.put("createdTargetVariable", createdTargetVariable);
                    }
                    return ok;
                } catch (Exception ex) {
                    String message = ex.getMessage() == null ? "" : ex.getMessage();
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("status", "ERROR");
                    err.put("error", message);
                    if (message.contains("/evt/") || message.toLowerCase(Locale.ROOT).contains("platformref")
                            || message.toLowerCase(Locale.ROOT).contains("event target")) {
                        err.put(
                                "hint",
                                "Event targets need slash refs: targetRef=@/evt/event1 (or eventName=event1). "
                                        + "virtual-unified events are event1/event2 — not Event1 or /evt/Event 1."
                        );
                    } else if (message.toLowerCase(Locale.ROOT).contains("unknown target variable")) {
                        err.put(
                                "hint",
                                "Create the target first: create_variable path=" + path
                                        + " name=<targetVariable> valueType=DOUBLE writable=false, "
                                        + "then retry create_binding_rule."
                        );
                    }
                    return err;
                }
            }

            private BindingActivators buildActivators(
                    Map<String, Object> arguments,
                    String path,
                    BindingRuleKind ruleKind
            ) {
                String remotePath = optionalString(arguments, "remoteObjectPath");
                String remoteVar = optionalString(arguments, "remoteVariableName");
                Integer periodicMs = intArg(arguments, "periodicMs", null);
                if (remotePath != null && remoteVar != null) {
                    long period = periodicMs != null ? periodicMs.longValue()
                            : (ruleKind == BindingRuleKind.HISTORIAN ? 60_000L : 0L);
                    return new BindingActivators(
                            boolArg(arguments, "onStartup", false),
                            List.of(com.ispf.core.binding.BindingVariableRef.remote(remotePath, remoteVar)),
                            null,
                            period
                    );
                }
                if (boolArg(arguments, "onContextChange", false)) {
                    return new BindingActivators(
                            boolArg(arguments, "onStartup", false),
                            List.of(com.ispf.core.binding.BindingVariableRef.local("@dashboardContext")),
                            null,
                            0,
                            false,
                            true
                    );
                }
                if (ruleKind == BindingRuleKind.HISTORIAN) {
                    long period = periodicMs != null ? periodicMs.longValue() : 60_000L;
                    BindingActivators defaults = BindingRulesService.defaultActivators(path, stringArg(arguments, "expression"));
                    return new BindingActivators(
                            boolArg(arguments, "onStartup", false),
                            defaults.onVariableChange(),
                            null,
                            period
                    );
                }
                return BindingRulesService.defaultActivators(path, stringArg(arguments, "expression"));
            }

            private static BindingRuleKind parseRuleKind(Map<String, Object> arguments) {
                String raw = stringArg(arguments, "ruleKind");
                if (raw.isBlank()) {
                    raw = stringArg(arguments, "kind");
                }
                if (raw.isBlank()) {
                    return BindingRuleKind.REACTIVE;
                }
                try {
                    return BindingRuleKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    return BindingRuleKind.REACTIVE;
                }
            }

            private com.ispf.core.binding.BindingTarget buildTarget(
                    Map<String, Object> arguments,
                    String targetKind,
                    String targetVariable
            ) {
                String field = optionalString(arguments, "targetField");
                if (field == null || field.isBlank()) {
                    field = "value";
                }
                String targetRef = stringArg(arguments, "targetRef");
                if (targetRef != null && !targetRef.isBlank()) {
                    return new com.ispf.core.binding.BindingTarget(
                            targetKind,
                            null,
                            field,
                            null,
                            null,
                            targetRef
                    );
                }
                return switch (targetKind) {
                    case com.ispf.core.binding.BindingTargetKind.CONTEXT -> new com.ispf.core.binding.BindingTarget(
                            com.ispf.core.binding.BindingTargetKind.CONTEXT,
                            null,
                            field,
                            stringArg(arguments, "contextPath"),
                            null
                    );
                    case com.ispf.core.binding.BindingTargetKind.EVENT -> new com.ispf.core.binding.BindingTarget(
                            com.ispf.core.binding.BindingTargetKind.EVENT,
                            null,
                            field,
                            null,
                            stringArg(arguments, "eventName")
                    );
                    case com.ispf.core.binding.BindingTargetKind.ACTION -> new com.ispf.core.binding.BindingTarget(
                            com.ispf.core.binding.BindingTargetKind.ACTION,
                            null,
                            field,
                            null,
                            null
                    );
                    default -> new com.ispf.core.binding.BindingTarget(targetVariable, field);
                };
            }
        };
    }

    private static PlatformAgentTool configureVariableHistoryTool(
            ObjectTreePort objectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_variable_history";
            }

            @Override
            public String description() {
                return "Enable or disable historian for an existing variable (required for chart/sparkline widgets). "
                        + "Args: path (object path), name (exact variable id from list_variables — "
                        + "virtual-unified: sineWave/intValue/floatValue/table, not Int/Float), "
                        + "historyEnabled (boolean), optional historyRetentionDays (integer).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String name = stringArg(arguments, "name");
                if (path.isBlank() || name.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path and name are required");
                }
                Boolean historyEnabled = boolArg(arguments, "historyEnabled", null);
                if (historyEnabled == null) {
                    return Map.of("status", "ERROR", "error", "historyEnabled is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    PlatformObject host = objectTreePort.require(path);
                    String resolvedName = AgentVariableNames.resolve(host, name);
                    if (host.getVariable(resolvedName).isEmpty()) {
                        return AgentVariableNames.unknownVariableError(host, name);
                    }
                    Integer retentionDays = intArg(arguments, "historyRetentionDays", null);
                    Variable updated = objectTreePort.updateVariableHistory(
                            path,
                            resolvedName,
                            historyEnabled,
                            retentionDays,
                            null,
                            null,
                            null,
                            null
                    );
                    Map<String, Object> preview = new LinkedHashMap<>();
                    preview.put("name", updated.name());
                    preview.put("historyEnabled", updated.historyEnabled());
                    updated.historyRetentionDays().ifPresent(days -> preview.put("historyRetentionDays", days));
                    preview.put("historySampleMode", updated.historySampleMode().name());
                    preview.put("includePreviousValueInEvent", updated.includePreviousValueInEvent());
                    preview.put("storageMode", updated.storageMode().name());
                    updated.telemetryPublishModeOverride()
                            .ifPresent(mode -> preview.put("telemetryPublishMode", mode));
                    Map<String, Object> ok = new LinkedHashMap<>();
                    ok.put("status", "OK");
                    ok.put("path", path);
                    ok.put("variable", preview);
                    if (!resolvedName.equals(name)) {
                        ok.put("resolvedName", resolvedName);
                        ok.put("requestedName", name);
                    }
                    return ok;
                } catch (Exception ex) {
                    String message = ex.getMessage() == null ? "" : ex.getMessage();
                    if (message.toLowerCase(Locale.ROOT).contains("unknown variable")) {
                        try {
                            return AgentVariableNames.unknownVariableError(objectTreePort.require(path), name);
                        } catch (Exception ignored) {
                            // fall through
                        }
                    }
                    return Map.of("status", "ERROR", "error", message);
                }
            }
        };
    }

    private static PlatformAgentTool configureOperatorUiTool(OperatorAppUiService operatorAppUiService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_operator_ui";
            }

            @Override
            public String description() {
                return "Set operator HMI default dashboard and menu. Args: appId (default platform), title, "
                        + "defaultDashboard (path), dashboards (list of {path, title}). "
                        + "Creates operator app record if appId is new.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                try {
                    String appId;
                    try {
                        appId = normalizeOperatorAppId(stringArg(arguments, "appId"));
                    } catch (IllegalArgumentException ex) {
                        return Map.of("status", "ERROR", "error", ex.getMessage());
                    }
                    String title = stringArg(arguments, "title");
                    String defaultDashboard = stringArg(arguments, "defaultDashboard");
                    Object dashboardsRaw = arguments.get("dashboards");
                    if (title.isBlank() || defaultDashboard.isBlank() || !(dashboardsRaw instanceof List<?> list) || list.isEmpty()) {
                        Map<String, Object> current;
                        try {
                            current = operatorAppUiService.getUi(appId);
                        } catch (IllegalArgumentException ex) {
                            return AgentToolErrors.error(
                                    "TOOL_ERROR",
                                    "configure_operator_ui requires title, defaultDashboard (dashboard path), "
                                            + "dashboards=[{path,title},...]. "
                                            + "appId=" + appId + " is a short id (e.g. virt-cluster), NOT a tree path. "
                                            + "Do NOT create_object APPLICATION — this tool creates the operator app record. "
                                            + "Example: appId=virt-cluster title=\"Cluster\" "
                                            + "defaultDashboard=root.platform.dashboards.virt-cluster-overview "
                                            + "dashboards=[{\"path\":\"root.platform.dashboards.virt-cluster-overview\","
                                            + "\"title\":\"Overview\"}]",
                                    "",
                                    ""
                            );
                        }
                        return Map.of("status", "OK", "appId", appId, "ui", current);
                    }
                    List<Map<String, String>> dashboards = new ArrayList<>();
                    for (Object item : (List<?>) dashboardsRaw) {
                        if (item instanceof Map<?, ?> row) {
                            String path = String.valueOf(row.get("path"));
                            String dashTitle = row.containsKey("title")
                                    ? String.valueOf(row.get("title"))
                                    : path;
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
                    } catch (IllegalArgumentException ex) {
                        operatorAppUiService.createApp(appId, title);
                    }
                    Map<String, Object> saved = operatorAppUiService.saveUi(
                            appId,
                            title,
                            defaultDashboard,
                            dashboards
                    );
                    return Map.of("status", "OK", "appId", appId, "ui", saved);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static Map<String, Object> alertPreview(AlertRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", rule.id());
        row.put("name", rule.name());
        row.put("targetObjectPath", rule.objectPath());
        row.put("watchVariable", rule.watchVariable());
        row.put("conditionExpr", rule.conditionExpr());
        row.put("eventName", rule.eventName());
        row.put("enabled", rule.enabled());
        return row;
    }

    private static Map<String, Object> correlatorPreview(EventCorrelator correlator) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", correlator.id());
        row.put("name", correlator.name());
        row.put("targetObjectPath", correlator.objectPath());
        row.put("patternType", correlator.patternType().name());
        row.put("eventName", correlator.eventName());
        row.put("enabled", correlator.enabled());
        return row;
    }

    private static Map<String, Object> alertSchema() {
        return Map.of(
                "parentPath", AutomationTreeService.ALERT_RULES_ROOT,
                "objectType", "ALERT",
                "model", "alert-rule-v1",
                "variables", List.of(
                        "targetObjectPath", "watchVariable", "conditionExpr", "eventName",
                        "payloadVariable", "enabled", "edgeTrigger", "delaySeconds", "sustainWhileTrue"
                ),
                "conditionExample", "self.temperature[\"value\"] > 85",
                "firePath", "ALERT rule node (rule path), not targetObjectPath",
                "alarmBarHint", "objectPathPrefix=root.platform.alert-rules, eventNames=[eventName]",
                "tool", "configure_alert"
        );
    }

    private static Map<String, Object> correlatorSchema() {
        return Map.of(
                "parentPath", AutomationTreeService.CORRELATORS_ROOT,
                "objectType", "CORRELATOR",
                "model", "correlator-v1",
                "patternTypes", List.of("COUNT", "SEQUENCE", "EVENT_CHAIN", "WINDOW"),
                "actionTypes", List.of("RUN_WORKFLOW", "FIRE_EVENT", "SET_VARIABLE", "OPEN_OPERATOR_REPORT"),
                "variables", List.of(
                        "targetObjectPath", "patternType", "eventName", "secondEventName",
                        "windowSeconds", "minOccurrences", "cooldownSeconds", "actionType", "actionTarget",
                        "payloadFilterExpr", "enabled"
                ),
                "note", "COUNT correlator counts events per targetObjectPath; cross-device logic use SINGLETON hub + read(remote/ref) + alert",
                "tool", "configure_correlator"
        );
    }

    private static Map<String, Object> dashboardSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("objectType", "DASHBOARD");
        schema.put("layoutVariable", "layout");
        schema.putAll(AgentDashboardGuide.summary());
        schema.put("fieldSemantics", AgentWidgetPropertiesGuide.fieldSemantics());
        schema.put("widgetTypesDocumented", AgentWidgetPropertiesGuide.allTypeSpecs().size());
        schema.putAll(AgentWidgetCatalog.catalogSummary());
        schema.put("drillDown",
                "object-table: selectionKey + rowTargetDashboard + rowOpenMode=navigate; "
                        + "detail widgets use same selectionKey");
        schema.put("historian",
                "chart/sparkline/history-table require historyEnabled=true; use configure_variable_history");
        schema.put("catalogTool", "get_widget_catalog");
        schema.put("tools", List.of(
                "get_widget_catalog",
                "get_dashboard_layout",
                "set_dashboard_layout",
                "add_dashboard_widget",
                "configure_variable_history"
        ));
        return schema;
    }

    private static Map<String, Object> bindingSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("ref", "root.platform.devices.foo/temperature — read(@/temperature) for rule-local vars");
        schema.put("cel", "CEL expressions on same object, e.g. self.member1[\"value\"] > 0 && self.member2[\"value\"] > 0");
        schema.put("historian", Map.of(
                "ruleKind", "historian",
                "windowBucket", "1m|5m|1h",
                "expression", "avg(objectPath/variable, 1m)",
                "example", "create_binding_rule ruleKind=historian windowBucket=1m "
                        + "expression=avg(root.platform.devices.virt-cluster.dev-01/sineWave, 1m) "
                        + "targetVariable=avg1m",
                "prerequisite", "configure_variable_history historyEnabled=true on source",
                "avoid", "Do not use rolling-avg Mixin Blueprint for minute averages"
        ));
        schema.put("clusterErrorPattern",
                "1) historian avg в†’ avgVar; 2) reactive cluster-error: self.avgVar[\"value\"] > 0 "
                        + "(or combine avgs). Never put avg() into reactive CEL.");
        schema.put("platformBindings", List.of(
                "read", "call", "hysteresis", "counterRate", "unitConvert", "sqlBinding"
        ));
        schema.put("tool", "create_binding_rule");
        return schema;
    }

    private static Map<String, Object> operatorSchema() {
        return Map.of(
                "defaultAppId", "platform",
                "rest", "PUT /api/v1/operator-apps/{appId}/ui",
                "fields", List.of("title", "defaultDashboard", "dashboards[{path,title}]"),
                "tool", "configure_operator_ui"
        );
    }

    private static List<Map<String, String>> objectTypeGuide() {
        return List.of(
                Map.of("type", "DEVICE", "use", "Sensors, PLCs, simulators", "keyVars", "driverConfigJson, driverPointMappingsJson, status"),
                Map.of("type", "DASHBOARD", "use", "Operator screens", "keyVars", "title, layout, refreshIntervalMs"),
                Map.of("type", "CUSTOM", "use", "Device folder / object-query — not app hub", "keyVars", "user-defined via create_variable"),
                Map.of("type", "SINGLETON", "use", "App logic hub under singleton-blueprints", "keyVars", "KPIs, aggregations via create_variable + bindings"),
                Map.of("type", "ALERT", "use", "CEL rules в†’ events", "parent", AutomationTreeService.ALERT_RULES_ROOT),
                Map.of("type", "CORRELATOR", "use", "Event patterns", "parent", AutomationTreeService.CORRELATORS_ROOT),
                Map.of("type", "WORKFLOW", "use", "BPMN automation", "parent", "root.platform.workflows"),
                Map.of("type", "REPORT", "use", "Report definitions", "parent", "root.platform.reports"),
                Map.of("type", "MODEL_CATALOG", "use", "Blueprint definitions", "paths", "root.platform.mixin-blueprints, instance-types, singleton-blueprints")
        );
    }

    private static DataSchema schemaForValueType(String valueType) {
        FieldType fieldType = switch (valueType.trim().toUpperCase(Locale.ROOT)) {
            case "BOOLEAN", "BOOL" -> FieldType.BOOLEAN;
            case "STRING", "TEXT" -> FieldType.STRING;
            case "INTEGER", "INT" -> FieldType.INTEGER;
            default -> FieldType.DOUBLE;
        };
        return DataSchema.builder("agentVar")
                .field("value", fieldType)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static DataRecord toInitialRecord(ObjectMapper objectMapper, DataSchema schema, Object raw)
            throws Exception {
        FieldType fieldType = schema.fields().isEmpty()
                ? FieldType.DOUBLE
                : schema.fields().get(0).type();
        Object scalar = unwrapInitialScalar(objectMapper, raw);
        Object coerced = coerceInitialScalar(fieldType, scalar);
        return DataRecord.single(schema, Map.of("value", coerced));
    }

    @SuppressWarnings("unchecked")
    private static Object unwrapInitialScalar(ObjectMapper objectMapper, Object raw) throws Exception {
        if (raw instanceof Map<?, ?> map) {
            if (map.containsKey("value")) {
                return map.get("value");
            }
            if (map.size() == 1) {
                return map.values().iterator().next();
            }
            throw new IllegalArgumentException(
                    "initialValue map must be {\"value\": <scalar>} or a single-entry map; got keys=" + map.keySet()
            );
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{")) {
                Map<String, Object> parsed = objectMapper.readValue(trimmed, Map.class);
                return unwrapInitialScalar(objectMapper, parsed);
            }
            return trimmed;
        }
        return raw;
    }

    private static Object coerceInitialScalar(FieldType fieldType, Object raw) {
        if (raw == null) {
            return switch (fieldType) {
                case BOOLEAN -> false;
                case INTEGER -> 0;
                case LONG -> 0L;
                case DOUBLE -> 0.0;
                default -> "";
            };
        }
        if (raw instanceof Boolean bool) {
            return switch (fieldType) {
                case BOOLEAN -> bool;
                case INTEGER, LONG -> bool ? 1 : 0;
                case DOUBLE -> bool ? 1.0 : 0.0;
                default -> String.valueOf(bool);
            };
        }
        if (raw instanceof Number number) {
            return switch (fieldType) {
                case BOOLEAN -> number.doubleValue() != 0.0;
                case INTEGER -> number.intValue();
                case LONG -> number.longValue();
                case DOUBLE -> number.doubleValue();
                default -> String.valueOf(number);
            };
        }
        String text = String.valueOf(raw).trim();
        return switch (fieldType) {
            case BOOLEAN -> Boolean.parseBoolean(text) || "1".equals(text) || "true".equalsIgnoreCase(text);
            case INTEGER -> (int) Double.parseDouble(text);
            case LONG -> (long) Double.parseDouble(text);
            case DOUBLE -> Double.parseDouble(text);
            default -> text;
        };
    }

    /**
     * Operator UI uses a short appId, not a tree path. Strip known prefixes; reject other dotted paths.
     */
    static String normalizeOperatorAppId(String rawAppId) {
        String id = rawAppId == null ? "" : rawAppId.trim();
        if (id.isBlank()) {
            return "platform";
        }
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.startsWith("root.platform.operator-apps.")
                || lower.startsWith("root.platform.applications.")) {
            int dot = id.lastIndexOf('.');
            String shortId = id.substring(dot + 1).trim();
            if (shortId.isBlank()) {
                throw new IllegalArgumentException(
                        "appId tree path has empty leaf; use short id e.g. virt-cluster");
            }
            return shortId;
        }
        if (id.contains(".")) {
            throw new IllegalArgumentException(
                    "appId must be a short id (e.g. virt-cluster), not a tree path: " + id
                            + ". Example: appId=virt-cluster title=... defaultDashboard=... dashboards=[...]");
        }
        return id;
    }

    /** Coerce Event1 / Event 1 → event1 on eventName and /evt/ targetRef segments. */
    private static Map<String, Object> coerceEventBindingArgs(
            Map<String, Object> arguments,
            PlatformObject host
    ) {
        Map<String, Object> copy = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        String eventName = stringArg(copy, "eventName");
        if (!eventName.isBlank()) {
            copy.put("eventName", AgentDiscoveryTools.resolveEventName(host, eventName));
        }
        String targetRef = stringArg(copy, "targetRef");
        int evt = targetRef.lastIndexOf("/evt/");
        if (evt >= 0) {
            String prefix = targetRef.substring(0, evt + "/evt/".length());
            String segment = targetRef.substring(evt + "/evt/".length());
            if (!segment.isBlank()) {
                copy.put("targetRef", prefix + AgentDiscoveryTools.resolveEventName(host, segment));
            }
        }
        return copy;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }

    private static Boolean boolArg(Map<String, Object> args, String key, Boolean defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Integer intArg(Map<String, Object> args, String key, Integer defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
