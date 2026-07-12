package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingRule;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.platform.analytics.formula.BindingFormulaResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BlueprintBindingRulesMerger {

    private final BindingRulesService bindingRulesService;
    private final BindingDependencyIndex dependencyIndex;
    private final BindingRuleEngine bindingRuleEngine;
    private final BindingFormulaResolver bindingFormulaResolver;
    private final BlueprintAnalyticsFormulaSupport blueprintAnalyticsFormulaSupport;

    public BlueprintBindingRulesMerger(
            BindingRulesService bindingRulesService,
            BindingDependencyIndex dependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            BindingFormulaResolver bindingFormulaResolver,
            BlueprintAnalyticsFormulaSupport blueprintAnalyticsFormulaSupport
    ) {
        this.bindingRulesService = bindingRulesService;
        this.dependencyIndex = dependencyIndex;
        this.bindingRuleEngine = bindingRuleEngine;
        this.bindingFormulaResolver = bindingFormulaResolver;
        this.blueprintAnalyticsFormulaSupport = blueprintAnalyticsFormulaSupport;
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
        if (model.bindingRules().isEmpty()) {
            return;
        }
        blueprintAnalyticsFormulaSupport.mergeBlueprintFormulas(model);
        Map<String, String> resolvedParams = parameters != null ? parameters : Map.of();
        List<BindingRule> merged = new ArrayList<>(bindingRulesService.listRules(objectPath));
        Map<String, BindingRule> byId = new LinkedHashMap<>();
        for (BindingRule existing : merged) {
            byId.put(existing.id(), existing);
        }
        for (BlueprintBindingRule modelRule : model.bindingRules()) {
            BindingRule resolved = bindingFormulaResolver.resolve(resolve(modelRule, resolvedParams));
            byId.put(resolved.id(), resolved);
        }
        bindingRulesService.saveRules(objectPath, new ArrayList<>(byId.values()));
        dependencyIndex.rebuild(objectPath);
        if (evaluateRules) {
            bindingRuleEngine.runRulesForObject(objectPath);
        }
    }

    public void evaluateRulesForObject(String objectPath) {
        bindingRuleEngine.runRulesForObject(objectPath);
    }

    private static BindingRule resolve(BlueprintBindingRule modelRule, Map<String, String> parameters) {
        String expression = resolveParameters(modelRule.expression(), parameters);
        String condition = resolveParameters(modelRule.condition(), parameters);
        Map<String, String> formulaParams = modelRule.formulaParams();
        Map<String, String> mergedFormulaParams = formulaParams == null || formulaParams.isEmpty()
                ? Map.of()
                : new LinkedHashMap<>(formulaParams);
        if (!mergedFormulaParams.isEmpty() && parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                mergedFormulaParams.replaceAll((key, value) ->
                        value != null ? value.replace("${" + entry.getKey() + "}", entry.getValue()) : value);
            }
        }
        BindingRule base = modelRule.toBindingRule();
        return new BindingRule(
                base.id(),
                base.name(),
                base.enabled(),
                base.order(),
                base.kind(),
                base.activators(),
                condition,
                expression,
                base.target(),
                base.windowBucket(),
                base.rollupBuckets(),
                base.formulaRef(),
                mergedFormulaParams.isEmpty() ? base.formulaParams() : mergedFormulaParams,
                base.formulaScope(),
                base.formulaAppId()
        );
    }

    private static String resolveParameters(String expression, Map<String, String> parameters) {
        if (expression == null || parameters == null || parameters.isEmpty()) {
            return expression;
        }
        String resolved = expression;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            resolved = resolved.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }
}
