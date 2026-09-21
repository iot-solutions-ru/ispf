package com.ispf.server.application.bundle;

import com.ispf.core.object.FunctionDescriptor;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintException;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleAlertRule;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleAnalyticsFormula;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleBlueprint;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleCorrelator;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleFunction;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.CorrelatorActionType;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.formula.AnalyticsFormula;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaParameter;
import com.ispf.server.plugin.blueprint.BlueprintPersistenceService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single-artifact upserts used while applying a bundle manifest: alert rules, correlators,
 * script functions and blueprints (create-or-update against the current tree / registry state).
 * The loop, skip-if-exists and error bookkeeping live in {@link BundleTreeArtifactsApplier}.
 */
@Component
public class BundleArtifactDeployers {

    private final ObjectManager objectManager;
    private final AutomationTreeService automationTreeService;
    private final ApplicationFunctionStore functionStore;
    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintPersistenceService blueprintPersistence;
    private final ObjectMapper objectMapper;

    public BundleArtifactDeployers(
            ObjectManager objectManager,
            AutomationTreeService automationTreeService,
            ApplicationFunctionStore functionStore,
            BlueprintEngine blueprintEngine,
            BlueprintRegistry blueprintRegistry,
            BlueprintPersistenceService blueprintPersistence,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.automationTreeService = automationTreeService;
        this.functionStore = functionStore;
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintPersistence = blueprintPersistence;
        this.objectMapper = objectMapper;
    }

    public void deployAlertRule(BundleAlertRule rule) {
        String path = AutomationTreeService.rulePathForName(rule.name());
        if (objectManager.tree().findByPath(path).isPresent()) {
            automationTreeService.updateAlertRule(
                    path,
                    rule.name(),
                    rule.objectPath(),
                    rule.watchVariable(),
                    rule.conditionExpr(),
                    rule.eventName(),
                    rule.payloadVariable(),
                    rule.enabled(),
                    rule.edgeTrigger(),
                    rule.delaySeconds(),
                    rule.sustainWhileTrue(),
                    "HIGH",
                    false,
                    null,
                    null,
                    null,
                    null
            );
            automationTreeService.resetAlertRuleRuntimeState(path);
            return;
        }
        automationTreeService.createAlertRule(
                rule.name(),
                rule.objectPath(),
                rule.watchVariable(),
                rule.conditionExpr(),
                rule.eventName(),
                rule.payloadVariable(),
                rule.enabled() == null || rule.enabled(),
                rule.edgeTrigger() == null || rule.edgeTrigger(),
                rule.delaySeconds() != null ? rule.delaySeconds() : 0,
                rule.sustainWhileTrue() != null && rule.sustainWhileTrue(),
                "HIGH",
                false,
                null,
                null,
                null
        );
        automationTreeService.resetAlertRuleRuntimeState(path);
    }

    public void deployCorrelator(BundleCorrelator correlator) {
        String path = AutomationTreeService.correlatorPathForName(correlator.name());
        CorrelatorPatternType patternType = CorrelatorPatternType.valueOf(
                correlator.patternType() != null ? correlator.patternType() : "COUNT"
        );
        CorrelatorActionType actionType = CorrelatorActionType.valueOf(
                correlator.actionType() != null ? correlator.actionType() : "RUN_WORKFLOW"
        );
        if (objectManager.tree().findByPath(path).isPresent()) {
            automationTreeService.updateCorrelator(
                    path,
                    correlator.name(),
                    correlator.objectPath(),
                    patternType,
                    correlator.eventName(),
                    correlator.secondEventName(),
                    correlator.windowSeconds(),
                    correlator.minOccurrences(),
                    correlator.cooldownSeconds(),
                    correlator.sequenceGapSeconds(),
                    actionType,
                    correlator.actionTarget(),
                    correlator.payloadFilterExpr(),
                    correlator.enabled()
            );
            return;
        }
        automationTreeService.createCorrelator(
                correlator.name(),
                correlator.objectPath(),
                patternType,
                correlator.eventName(),
                correlator.secondEventName(),
                correlator.windowSeconds() != null ? correlator.windowSeconds() : 0,
                correlator.minOccurrences() != null ? correlator.minOccurrences() : 1,
                correlator.cooldownSeconds() != null ? correlator.cooldownSeconds() : 120,
                correlator.sequenceGapSeconds() != null ? correlator.sequenceGapSeconds() : 0,
                actionType,
                correlator.actionTarget(),
                correlator.payloadFilterExpr(),
                correlator.enabled() == null || correlator.enabled()
        );
    }

    /** Stores the versioned function source and mirrors it as a tree function on the target object. */
    public void deployFunction(String appId, String dataSourcePath, BundleFunction function) throws Exception {
        String version = function.version() != null ? function.version() : "1";
        String inputSchemaJson = objectMapper.writeValueAsString(function.descriptor().inputSchema());
        String outputSchemaJson = objectMapper.writeValueAsString(function.descriptor().outputSchema());

        ApplicationFunctionHandler.DeployedFunction deployed = new ApplicationFunctionHandler.DeployedFunction(
                UUID.randomUUID(),
                appId,
                function.objectPath(),
                function.functionName(),
                version,
                function.source().type(),
                function.source().body(),
                inputSchemaJson,
                outputSchemaJson
        );
        functionStore.deploy(deployed);

        FunctionDescriptor treeFunction = new FunctionDescriptor(
                function.functionName(),
                "Script function " + function.functionName(),
                function.descriptor().inputSchema(),
                function.descriptor().outputSchema(),
                function.source().type(),
                function.source().body(),
                dataSourcePath,
                version
        );
        objectManager.upsertFunction(function.objectPath(), treeFunction);
    }

    /** Creates or updates a blueprint; unspecified fields of an update keep the existing values. */
    public void deployBlueprint(BundleBlueprint blueprint) throws BlueprintException {
        Instant now = Instant.now();
        var existingReg = blueprintRegistry.findByName(blueprint.name());
        // Reuse DB id when a stale builtin row remains after mes-catalog was disabled
        // (UNIQUE name would otherwise fail INSERT with a fresh UUID).
        String id = existingReg.map(BlueprintDefinition::id)
                .or(() -> blueprintPersistence.findIdByName(blueprint.name()))
                .orElseGet(() -> UUID.randomUUID().toString());
        Instant createdAt = existingReg.map(BlueprintDefinition::createdAt).orElse(now);
        BlueprintDefinition definition = existingReg
                .map(existing -> new BlueprintDefinition(
                        id,
                        blueprint.name(),
                        blueprint.description(),
                        blueprint.type() != null ? blueprint.type() : existing.type(),
                        blueprint.targetObjectType() != null ? blueprint.targetObjectType() : existing.targetObjectType(),
                        blueprint.suitabilityExpression() != null
                                ? blueprint.suitabilityExpression()
                                : existing.suitabilityExpression(),
                        blueprint.variables() != null ? blueprint.variables() : existing.variables(),
                        blueprint.events() != null ? blueprint.events() : existing.events(),
                        blueprint.functions() != null ? blueprint.functions() : existing.functions(),
                        blueprint.bindings() != null ? blueprint.bindings() : existing.bindings(),
                        blueprint.parameters() != null ? blueprint.parameters() : existing.parameters(),
                        createdAt,
                        now
                ))
                .orElseGet(() -> new BlueprintDefinition(
                        id,
                        blueprint.name(),
                        blueprint.description(),
                        blueprint.type(),
                        blueprint.targetObjectType(),
                        blueprint.suitabilityExpression(),
                        blueprint.variables() != null ? blueprint.variables() : List.of(),
                        blueprint.events() != null ? blueprint.events() : List.of(),
                        blueprint.functions() != null ? blueprint.functions() : List.of(),
                        blueprint.bindings() != null ? blueprint.bindings() : List.of(),
                        blueprint.parameters() != null ? blueprint.parameters() : Map.of(),
                        createdAt,
                        now
                ));

        BlueprintDefinition saved = existingReg.isPresent()
                ? blueprintEngine.updateBlueprint(definition)
                : blueprintEngine.createBlueprint(definition);
        blueprintPersistence.persist(saved, false);
    }

    static AnalyticsFormula toAnalyticsFormula(String appId, BundleAnalyticsFormula formula) {
        List<AnalyticsFormulaParameter> parameters = formula.parameters() == null
                ? List.of()
                : formula.parameters().stream()
                        .map(param -> new AnalyticsFormulaParameter(
                                param.name(),
                                param.type(),
                                param.required(),
                                param.description(),
                                param.defaultValue()
                        ))
                        .toList();
        return new AnalyticsFormula(
                formula.id(),
                formula.displayName(),
                formula.kind(),
                formula.expression(),
                parameters,
                formula.createdBy(),
                formula.version() != null ? formula.version() : 1,
                AnalyticsFormula.SCOPE_APP,
                appId
        );
    }
}
