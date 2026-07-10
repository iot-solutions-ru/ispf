package com.ispf.plugin.blueprint;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingTarget;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        String targetField,
        String kind,
        String windowBucket,
        List<String> rollupBuckets,
        String formulaRef,
        Map<String, String> formulaParams,
        String formulaScope,
        String formulaAppId
) {
    public BlueprintBindingRule {
        if (rollupBuckets != null) {
            rollupBuckets = List.copyOf(rollupBuckets);
        }
        if (formulaParams != null) {
            formulaParams = Map.copyOf(formulaParams);
        }
    }

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
                "value",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public BlueprintBindingRule(
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
        this(id, name, enabled, order, activators, condition, expression, targetVariable, targetField, null, null, null, null, null, null, null);
    }

    public BindingRule toBindingRule() {
        BindingRuleKind parsedKind = BindingRuleKind.REACTIVE;
        if (kind != null && !kind.isBlank()) {
            parsedKind = BindingRuleKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        }
        return new BindingRule(
                id,
                name != null ? name : id,
                enabled == null || enabled,
                order != null ? order : 0,
                parsedKind,
                activators,
                condition,
                expression,
                new BindingTarget(targetVariable, targetField),
                windowBucket,
                rollupBuckets,
                formulaRef,
                formulaParams,
                formulaScope,
                formulaAppId
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
                rule.target().field(),
                rule.kind().name().toLowerCase(Locale.ROOT),
                rule.windowBucket(),
                rule.rollupBuckets(),
                rule.formulaRef(),
                rule.formulaParams(),
                rule.formulaScope(),
                rule.formulaAppId()
        );
    }
}
