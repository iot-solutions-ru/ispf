package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.platform.analytics.formula.BindingFormulaResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BlueprintBindingRulesMerger {

    private static final Logger log = LoggerFactory.getLogger(BlueprintBindingRulesMerger.class);

    public static final String NOT_EXPORTED = "notExported";

    public static final String NOT_EXPORTED_SECTIONS = "sqlBindings,alertRules";

    private final BindingRulesService bindingRulesService;
    private final BindingDependencyIndex dependencyIndex;
    private final BindingRuleEngine bindingRuleEngine;
    private final BindingFormulaResolver bindingFormulaResolver;
    private final BlueprintAnalyticsFormulaSupport blueprintAnalyticsFormulaSupport;
    private final BlueprintParameterResolver parameterResolver;
    private final BlueprintRegistry blueprintRegistry;

    public BlueprintBindingRulesMerger(
            BindingRulesService bindingRulesService,
            BindingDependencyIndex dependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            BindingFormulaResolver bindingFormulaResolver,
            BlueprintAnalyticsFormulaSupport blueprintAnalyticsFormulaSupport,
            BlueprintParameterResolver parameterResolver
    ) {
        this(
                bindingRulesService,
                dependencyIndex,
                bindingRuleEngine,
                bindingFormulaResolver,
                blueprintAnalyticsFormulaSupport,
                parameterResolver,
                null
        );
    }

    @Autowired
    public BlueprintBindingRulesMerger(
            BindingRulesService bindingRulesService,
            BindingDependencyIndex dependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            BindingFormulaResolver bindingFormulaResolver,
            BlueprintAnalyticsFormulaSupport blueprintAnalyticsFormulaSupport,
            BlueprintParameterResolver parameterResolver,
            BlueprintRegistry blueprintRegistry
    ) {
        this.bindingRulesService = bindingRulesService;
        this.dependencyIndex = dependencyIndex;
        this.bindingRuleEngine = bindingRuleEngine;
        this.bindingFormulaResolver = bindingFormulaResolver;
        this.blueprintAnalyticsFormulaSupport = blueprintAnalyticsFormulaSupport;
        this.parameterResolver = parameterResolver;
        this.blueprintRegistry = blueprintRegistry;
    }

    public void mergeBlueprintRules(String objectPath, BlueprintDefinition model, Map<String, String> parameters) {
        mergeBlueprintRules(objectPath, model, parameters, true);
    }

    public void mergeBlueprintRules(
            String objectPath,
            BlueprintDefinition model,
            Map<String, String> parameters,
            boolean evaluateRules
    ) {
        BlueprintDefinition effective = BlueprintEngine.flattenInheritance(model, blueprintRegistry);
        if (effective.bindingRules().isEmpty()) {
            return;
        }
        blueprintAnalyticsFormulaSupport.mergeBlueprintFormulas(effective);
        Map<String, String> resolvedParams = parameters != null ? parameters : Map.of();
        List<BindingRule> merged = new ArrayList<>(bindingRulesService.listRules(objectPath));
        Map<String, BindingRule> byId = new LinkedHashMap<>();
        for (BindingRule existing : merged) {
            byId.put(existing.id(), existing);
        }
        for (BlueprintBindingRule modelRule : effective.bindingRules()) {
            BindingRule resolved = bindingFormulaResolver.resolve(resolve(modelRule, resolvedParams, parameterResolver));
            byId.put(resolved.id(), resolved);
        }
        bindingRulesService.saveRules(objectPath, new ArrayList<>(byId.values()));
        dependencyIndex.rebuild(objectPath);
        if (evaluateRules) {
            bindingRuleEngine.runRulesForObject(objectPath);
        }
    }

    /**
     * Startup-only merge for objects already instantiated from a blueprint.
     *
     * <p>Decides per rule. Template {@code model.parameters()} are never applied — instance
     * attach values are not stored on the object, so re-substituting defaults would wipe a
     * resolved saved expression. When the blueprint rule still contains unresolved placeholders,
     * the saved instance rule is kept; missing or still-unresolved saved rules are reported for
     * readiness DOWN. Rules without leftover placeholders are merged with an empty parameter map.
     *
     * <p>Nothing is evaluated here: this runs inside {@code ObjectManager.initialize()}, and
     * {@code BindingRulesStartupRunner} fires {@code onStartup} rules once the tree is ready.
     *
     * @param ruleIdsOwnedElsewhere rule ids another blueprint on the same object owns; left as saved
     * @return templated rules whose saved instance rule is missing or unresolved (empty when healthy)
     */
    public List<BlueprintStartupRulesHealth.Problem> syncBlueprintRulesAtStartup(
            String objectPath,
            BlueprintDefinition model,
            Set<String> ruleIdsOwnedElsewhere
    ) {
        BlueprintDefinition effective = BlueprintEngine.flattenInheritance(model, blueprintRegistry);
        if (effective.bindingRules().isEmpty()) {
            return List.of();
        }
        blueprintAnalyticsFormulaSupport.mergeBlueprintFormulas(effective);
        Map<String, BindingRule> byId = new LinkedHashMap<>();
        for (BindingRule existing : bindingRulesService.listRules(objectPath)) {
            byId.put(existing.id(), existing);
        }
        List<BlueprintStartupRulesHealth.Problem> problems = new ArrayList<>();
        for (BlueprintBindingRule modelRule : effective.bindingRules()) {
            if (ruleIdsOwnedElsewhere.contains(modelRule.id())) {
                continue;
            }
            if (UnresolvedBindingPlaceholders.present(modelRule)) {
                BindingRule saved = byId.get(modelRule.id());
                if (saved == null) {
                    problems.add(problem(
                            objectPath, effective, modelRule, BlueprintStartupRulesHealth.Kind.MISSING
                    ));
                } else if (UnresolvedBindingPlaceholders.present(saved)) {
                    problems.add(problem(
                            objectPath, effective, modelRule, BlueprintStartupRulesHealth.Kind.UNRESOLVED
                    ));
                }
                // Keep saved as deployed; do not overlay blueprint defaults.
            } else {
                BindingRule resolved = bindingFormulaResolver.resolve(
                        resolve(modelRule, Map.of(), parameterResolver)
                );
                byId.put(resolved.id(), resolved);
            }
        }
        bindingRulesService.saveRulesAllowingUnresolvedPlaceholders(objectPath, new ArrayList<>(byId.values()));
        dependencyIndex.rebuild(objectPath);
        return problems;
    }

    private static BlueprintStartupRulesHealth.Problem problem(
            String objectPath,
            BlueprintDefinition model,
            BlueprintBindingRule modelRule,
            BlueprintStartupRulesHealth.Kind kind
    ) {
        BlueprintStartupRulesHealth.Problem problem =
                BlueprintStartupRulesHealth.Problem.of(objectPath, model, modelRule.id(), kind);
        log.warn("{}", problem.message());
        return problem;
    }

    public void removeBlueprintRules(String objectPath, List<String> bindingRuleIds) {
        if (bindingRuleIds == null || bindingRuleIds.isEmpty()) {
            return;
        }
        Set<String> remove = Set.copyOf(bindingRuleIds);
        List<BindingRule> remaining = bindingRulesService.listRules(objectPath).stream()
                .filter(rule -> !remove.contains(rule.id()))
                .toList();
        // A sibling kept unresolved at startup must not block detach.
        bindingRulesService.saveRulesAllowingUnresolvedPlaceholders(objectPath, remaining);
        dependencyIndex.rebuild(objectPath);
    }

    public void evaluateRulesForObject(String objectPath) {
        bindingRuleEngine.runRulesForObject(objectPath);
    }

    /**
     * Copies live binding rules of the source object onto a model taken from that object.
     * SQL bindings and alert rules are not part of this snapshot; the model records that omission.
     */
    public BlueprintDefinition captureFromObject(String sourcePath, BlueprintDefinition model) {
        List<BlueprintBindingRule> rules = bindingRulesService.listRules(sourcePath).stream()
                .map(BlueprintBindingRule::fromBindingRule)
                .toList();
        Map<String, String> parameters = new LinkedHashMap<>(model.parameters());
        parameters.put(NOT_EXPORTED, NOT_EXPORTED_SECTIONS);
        return new BlueprintDefinition(
                model.id(),
                model.name(),
                model.description(),
                model.type(),
                model.targetObjectType(),
                model.suitabilityExpression(),
                model.variables(),
                model.events(),
                model.functions(),
                rules,
                model.sqlBindings(),
                model.alertRules(),
                parameters,
                model.createdAt(),
                model.updatedAt()
        );
    }

    private static BindingRule resolve(
            BlueprintBindingRule modelRule,
            Map<String, String> parameters,
            BlueprintParameterResolver parameterResolver
    ) {
        Map<String, String> formulaParams = modelRule.formulaParams();
        Map<String, String> mergedFormulaParams = formulaParams == null || formulaParams.isEmpty()
                ? modelRule.formulaParams()
                : parameterResolver.resolveValues(formulaParams, parameters);
        List<String> resolvedRollupBuckets = modelRule.rollupBuckets() == null
                ? null
                : modelRule.rollupBuckets().stream()
                        .map(bucket -> parameterResolver.resolve(bucket, parameters))
                        .toList();
        // Keep id literal: ownership manifests and detach match the template id, not a
        // per-instance substituted value.
        BlueprintBindingRule resolvedRule = new BlueprintBindingRule(
                modelRule.id(),
                parameterResolver.resolve(modelRule.name(), parameters),
                modelRule.enabled(),
                modelRule.order(),
                resolveActivators(modelRule.activators(), parameters, parameterResolver),
                parameterResolver.resolve(modelRule.condition(), parameters),
                parameterResolver.resolve(modelRule.expression(), parameters),
                parameterResolver.resolve(modelRule.targetVariable(), parameters),
                parameterResolver.resolve(modelRule.targetField(), parameters),
                modelRule.kind(),
                parameterResolver.resolve(modelRule.windowBucket(), parameters),
                resolvedRollupBuckets,
                parameterResolver.resolve(modelRule.formulaRef(), parameters),
                mergedFormulaParams,
                parameterResolver.resolve(modelRule.formulaScope(), parameters),
                parameterResolver.resolve(modelRule.formulaAppId(), parameters),
                resolveTarget(modelRule.target(), parameters, parameterResolver)
        );
        return resolvedRule.toBindingRule();
    }

    private static BindingTarget resolveTarget(
            BindingTarget target,
            Map<String, String> parameters,
            BlueprintParameterResolver parameterResolver
    ) {
        if (target == null) {
            return null;
        }
        return new BindingTarget(
                parameterResolver.resolve(target.kind(), parameters),
                parameterResolver.resolve(target.variableName(), parameters),
                parameterResolver.resolve(target.field(), parameters),
                parameterResolver.resolve(target.path(), parameters),
                parameterResolver.resolve(target.eventName(), parameters),
                parameterResolver.resolve(target.ref(), parameters)
        );
    }

    private static BindingActivators resolveActivators(
            BindingActivators activators,
            Map<String, String> parameters,
            BlueprintParameterResolver parameterResolver
    ) {
        if (activators == null) {
            return null;
        }
        List<BindingVariableRef> resolvedVars = activators.onVariableChange().stream()
                .map(ref -> new BindingVariableRef(
                        parameterResolver.resolve(ref.objectPath(), parameters),
                        parameterResolver.resolve(ref.variableName(), parameters),
                        parameterResolver.resolve(ref.ref(), parameters)
                ))
                .toList();
        return new BindingActivators(
                activators.onStartup(),
                resolvedVars,
                parameterResolver.resolve(activators.onEvent(), parameters),
                activators.periodicMs(),
                activators.asyncFlag(),
                activators.onContextChangeFlag(),
                parameterResolver.resolve(activators.onEventRef(), parameters)
        );
    }
}
