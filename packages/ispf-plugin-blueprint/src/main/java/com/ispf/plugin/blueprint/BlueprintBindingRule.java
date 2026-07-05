package com.ispf.plugin.blueprint;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;

import java.util.List;

/**
 * Binding rule attached by a model.
 */
public record BlueprintBindingRule(
        String id,
        String name,
        Boolean enabled,
        Integer order,
        BindingActivators activators,
        String condition,
        String expression,
        String targetVariable,
        String targetField
) {
    public static BlueprintBindingRule of(String id, String targetVariable, String expression) {
        return new BlueprintBindingRule(
                id,
                targetVariable,
                true,
                0,
                null,
                "",
                expression,
                targetVariable,
                "value"
        );
    }

    public BindingRule toBindingRule() {
        return new BindingRule(
                id,
                name != null ? name : id,
                enabled == null || enabled,
                order != null ? order : 0,
                activators,
                condition,
                expression,
                new BindingTarget(targetVariable, targetField)
        );
    }

    public static BlueprintBindingRule fromBindingRule(BindingRule rule) {
        return new BlueprintBindingRule(
                rule.id(),
                rule.name(),
                rule.enabled(),
                rule.order(),
                rule.activators(),
                rule.condition(),
                rule.expression(),
                rule.target().variableName(),
                rule.target().field()
        );
    }
}
