package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingRule;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
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

    public BlueprintBindingRulesMerger(
            BindingRulesService bindingRulesService,
            BindingDependencyIndex dependencyIndex,
            BindingRuleEngine bindingRuleEngine
    ) {
        this.bindingRulesService = bindingRulesService;
        this.dependencyIndex = dependencyIndex;
        this.bindingRuleEngine = bindingRuleEngine;
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
        Map<String, String> resolvedParams = parameters != null ? parameters : Map.of();
        List<BindingRule> merged = new ArrayList<>(bindingRulesService.listRules(objectPath));
        Map<String, BindingRule> byId = new LinkedHashMap<>();
        for (BindingRule existing : merged) {
            byId.put(existing.id(), existing);
        }
        for (BlueprintBindingRule modelRule : model.bindingRules()) {
            BindingRule resolved = resolve(modelRule, resolvedParams);
            byId.put(resolved.id(), resolved);
        }
        bindingRulesService.saveRules(objectPath, new ArrayList<>(byId.values()));
        dependencyIndex.rebuild(objectPath);
        if (evaluateRules) {
            bindingRuleEngine.runRulesForObject(objectPath);
        }
    }

    private static BindingRule resolve(BlueprintBindingRule modelRule, Map<String, String> parameters) {
        String expression = resolveParameters(modelRule.expression(), parameters);
        String condition = resolveParameters(modelRule.condition(), parameters);
        return new BindingRule(
                modelRule.id(),
                modelRule.name(),
                modelRule.enabled() == null || modelRule.enabled(),
                modelRule.order() != null ? modelRule.order() : 0,
                modelRule.activators(),
                condition,
                expression,
                modelRule.toBindingRule().target()
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
