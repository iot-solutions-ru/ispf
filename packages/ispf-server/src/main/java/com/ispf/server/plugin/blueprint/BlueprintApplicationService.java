package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintAlertTemplate;
import com.ispf.plugin.blueprint.BlueprintApplyResult;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintDetachResult;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintException;
import com.ispf.plugin.blueprint.BlueprintMergeWarning;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintSqlBindingTemplate;
import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified blueprint apply path: structure merge, binding rules, persistence of appliedBlueprintIds.
 */
@Service
public class BlueprintApplicationService {

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintBindingRulesMerger bindingRulesMerger;
    private final ObjectManager objectManager;
    private final BlueprintParameterResolver parameterResolver;
    private final SqlBindingObjectService sqlBindingObjectService;
    private final AlertRuleService alertRuleService;

    public BlueprintApplicationService(
            BlueprintEngine blueprintEngine,
            BlueprintRegistry blueprintRegistry,
            BlueprintBindingRulesMerger bindingRulesMerger,
            ObjectManager objectManager,
            BlueprintParameterResolver parameterResolver,
            SqlBindingObjectService sqlBindingObjectService,
            AlertRuleService alertRuleService
    ) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
        this.bindingRulesMerger = bindingRulesMerger;
        this.objectManager = objectManager;
        this.parameterResolver = parameterResolver;
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.alertRuleService = alertRuleService;
    }

    @Transactional
    public BlueprintApplyResult applyBlueprintWithRules(String blueprintId, String objectPath) {
        return applyBlueprintWithRules(blueprintRegistry.requireById(blueprintId), objectPath, Map.of());
    }

    @Transactional
    public BlueprintApplyResult applyBlueprintWithRules(BlueprintDefinition model, String objectPath, Map<String, String> parameters) {
        try {
            if (com.ispf.plugin.blueprint.SystemIntrinsicBlueprints.isIntrinsic(model)) {
                BlueprintApplyResult result = blueprintEngine.applyIntrinsicStructure(model, objectPath);
                applyParameterizedContributions(model, objectPath, parameters);
                objectManager.persistNodeTree(objectPath);
                return result;
            }
            BlueprintApplyResult result = blueprintEngine.applyBlueprint(model.id(), objectPath);
            applyParameterizedContributions(model, objectPath, parameters);
            objectManager.persistNodeTree(objectPath);
            return result;
        } catch (BlueprintException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Transactional
    public BlueprintApplyResult instantiateWithRules(
            String blueprintId,
            String parentPath,
            String instanceName,
            Map<String, String> parameters
    ) {
        try {
            BlueprintApplyResult result = blueprintEngine.instantiateBlueprint(blueprintId, parentPath, instanceName, parameters);
            // Use in-memory tree only: object is not persisted yet; cluster require() evicts RAM-only nodes.
            String instancePath = objectManager.tree().resolveChildPath(parentPath, instanceName);
            PlatformObject instance = objectManager.tree().require(instancePath);
            BlueprintDefinition model = blueprintRegistry.requireById(blueprintId);
            objectManager.persistNodeTree(instance.path());
            applyParameterizedContributions(instance, model, parameters);
            List<BlueprintApplyResult> mixinResults = blueprintEngine.applyMixinBlueprints(instance.path());
            for (BlueprintApplyResult relative : mixinResults) {
                blueprintRegistry.findById(relative.attachment().blueprintId()).ifPresent(relativeModel ->
                        applyParameterizedContributions(instance, relativeModel, relativeModel.parameters())
                );
            }
            objectManager.persistNodeTree(instance.path());
            return aggregateResults(result, mixinResults);
        } catch (BlueprintException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Transactional
    public List<BlueprintApplyResult> applyMixinBlueprintsWithRules(String objectPath) {
        List<BlueprintApplyResult> results = blueprintEngine.applyMixinBlueprints(objectPath);
        for (BlueprintApplyResult result : results) {
            blueprintRegistry.findById(result.attachment().blueprintId()).ifPresent(model ->
                    applyParameterizedContributions(model, objectPath, model.parameters())
            );
        }
        if (!results.isEmpty()) {
            objectManager.persistNodeTree(objectPath);
        }
        return results;
    }

    @Transactional
    public BlueprintDetachResult detachBlueprintWithRules(String blueprintId, String objectPath) {
        try {
            BlueprintDetachResult result = blueprintEngine.detachBlueprint(blueprintId, objectPath);
            if (result.detached()) {
                bindingRulesMerger.removeBlueprintRules(objectPath, result.removedBindingRuleIds());
                for (String variableName : result.removedVariables()) {
                    objectManager.purgeVariablePersistence(objectPath, variableName);
                }
                objectManager.persistNodeTree(objectPath);
            }
            return result;
        } catch (BlueprintException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public void restoreAttachments() {
        blueprintEngine.restoreAttachmentsFromObjects();
    }

    @Transactional
    public PlatformObject ensureSingletonInstanceWithRules(String blueprintId) {
        try {
            BlueprintDefinition model = blueprintRegistry.requireById(blueprintId);
            PlatformObject instance = blueprintEngine.ensureSingletonInstance(model);
            applyParameterizedContributions(instance, model, model.parameters());
            objectManager.persistNodeTree(instance.path());
            return objectManager.require(instance.path());
        } catch (BlueprintException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private void applyParameterizedContributions(BlueprintDefinition model, String objectPath, Map<String, String> parameters) {
        applyParameterizedContributions(objectManager.require(objectPath), model, parameters);
    }

    private void applyParameterizedContributions(
            PlatformObject instance,
            BlueprintDefinition model,
            Map<String, String> parameters
    ) {
        Map<String, String> resolvedParams = parameterResolver.parametersFor(
                instance,
                mergeParameters(model.parameters(), parameters)
        );
        bindingRulesMerger.mergeBlueprintRules(instance.path(), model, resolvedParams);
        applySqlBindings(instance.path(), model, resolvedParams);
        applyAlertRules(instance.path(), model, resolvedParams);
    }

    private void applySqlBindings(String instancePath, BlueprintDefinition model, Map<String, String> parameters) {
        for (BlueprintSqlBindingTemplate template : model.sqlBindings()) {
            String variable = parameterResolver.resolve(template.variable(), parameters);
            if (variable == null || variable.isBlank()) {
                continue;
            }
            String bindingId = model.name() + "-" + instancePath + "-" + variable;
            sqlBindingObjectService.upsert(new SqlBindingObjectService.BindingDefinition(
                    "",
                    bindingId,
                    instancePath,
                    variable,
                    parameterResolver.resolve(template.dataSourcePath(), parameters),
                    parameterResolver.resolve(template.query(), parameters),
                    valueOrDefault(parameterResolver.resolve(template.valueField(), parameters), "value"),
                    "on_schedule",
                    template.refreshIntervalMs() != null ? template.refreshIntervalMs() : 30_000L,
                    "",
                    "",
                    true,
                    null
            ));
        }
    }

    private void applyAlertRules(String instancePath, BlueprintDefinition model, Map<String, String> parameters) {
        for (BlueprintAlertTemplate template : model.alertRules()) {
            String name = parameterResolver.resolve(template.name(), parameters);
            if (name == null || name.isBlank()) {
                name = model.name() + "-" + leafName(instancePath) + "-" + template.watchVariable();
            }
            String rulePath = AutomationTreeService.rulePathForName(name);
            if (objectManager.tree().findByPath(rulePath).isPresent()) {
                alertRuleService.update(rulePath, new AlertRuleService.UpdateAlertRuleRequest(
                        name,
                        instancePath,
                        parameterResolver.resolve(template.watchVariable(), parameters),
                        parameterResolver.resolve(template.conditionExpr(), parameters),
                        parameterResolver.resolve(template.eventName(), parameters),
                        parameterResolver.resolve(template.payloadVariable(), parameters),
                        template.enabled(),
                        template.edgeTrigger(),
                        template.delaySeconds(),
                        template.sustainWhileTrue(),
                        parameterResolver.resolve(template.severity(), parameters),
                        template.ackRequired(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        template.pollIntervalMs(),
                        parameterResolver.resolve(template.triggerMessage(), parameters),
                        parameterResolver.resolve(template.clearEventName(), parameters)
                ));
            } else {
                alertRuleService.create(new AlertRuleService.CreateAlertRuleRequest(
                        name,
                        instancePath,
                        parameterResolver.resolve(template.watchVariable(), parameters),
                        parameterResolver.resolve(template.conditionExpr(), parameters),
                        parameterResolver.resolve(template.eventName(), parameters),
                        parameterResolver.resolve(template.payloadVariable(), parameters),
                        template.enabled() == null || template.enabled(),
                        template.edgeTrigger() == null || template.edgeTrigger(),
                        template.delaySeconds(),
                        template.sustainWhileTrue(),
                        parameterResolver.resolve(template.severity(), parameters),
                        template.ackRequired(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        template.pollIntervalMs(),
                        parameterResolver.resolve(template.triggerMessage(), parameters),
                        parameterResolver.resolve(template.clearEventName(), parameters)
                ));
            }
        }
    }

    private static Map<String, String> mergeParameters(Map<String, String> modelParameters, Map<String, String> parameters) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (modelParameters != null) {
            merged.putAll(modelParameters);
        }
        if (parameters != null) {
            merged.putAll(parameters);
        }
        return merged;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static String leafName(String path) {
        int lastDot = path != null ? path.lastIndexOf('.') : -1;
        return lastDot >= 0 ? path.substring(lastDot + 1) : valueOrDefault(path, "object");
    }

    private static BlueprintApplyResult aggregateResults(BlueprintApplyResult primary, List<BlueprintApplyResult> additional) {
        List<BlueprintMergeWarning> warnings = new ArrayList<>(primary.warnings());
        for (BlueprintApplyResult result : additional) {
            warnings.addAll(result.warnings());
        }
        return new BlueprintApplyResult(primary.attachment(), warnings);
    }
}
