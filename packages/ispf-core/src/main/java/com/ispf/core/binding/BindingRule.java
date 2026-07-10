package com.ispf.core.binding;

import java.util.List;
import java.util.Map;

/**
 * Declarative binding: activator (when) → condition (if) → expression (how) → target (where).
 *
 * <p>{@link BindingRuleKind#HISTORIAN} rules are compiled into analytics tags (ADR-0041).
 */
public record BindingRule(
        String id,
        String name,
        boolean enabled,
        int order,
        BindingRuleKind kind,
        BindingActivators activators,
        String condition,
        String expression,
        BindingTarget target,
        String windowBucket,
        List<String> rollupBuckets,
        String formulaRef,
        Map<String, String> formulaParams,
        String formulaScope,
        String formulaAppId
) {
    public BindingRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Binding rule id is required");
        }
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Binding rule expression is required");
        }
        if (target == null) {
            throw new IllegalArgumentException("Binding rule target is required");
        }
        if (target.isVariable() && (target.variableName() == null || target.variableName().isBlank())) {
            throw new IllegalArgumentException("Binding rule target.variableName is required");
        }
        if (target.isContext() && (target.path() == null || target.path().isBlank())) {
            throw new IllegalArgumentException("Binding rule target.path is required for context effect");
        }
        if (target.isEvent() && (target.eventName() == null || target.eventName().isBlank())) {
            throw new IllegalArgumentException("Binding rule target.eventName is required for event effect");
        }
        if (kind == null) {
            kind = BindingRuleKind.REACTIVE;
        }
        if (activators == null) {
            activators = BindingActivators.onLocalChange();
        }
        if (condition == null) {
            condition = "";
        }
        if (name == null) {
            name = id;
        }
        if (rollupBuckets != null) {
            rollupBuckets = List.copyOf(rollupBuckets);
        }
        if (formulaParams != null) {
            formulaParams = Map.copyOf(formulaParams);
        }
    }

    public BindingRule(
            String id,
            String name,
            boolean enabled,
            int order,
            BindingActivators activators,
            String condition,
            String expression,
            BindingTarget target
    ) {
        this(id, name, enabled, order, BindingRuleKind.REACTIVE, activators, condition, expression, target, null, null, null, null, null, null);
    }

    public BindingRule(
            String id,
            String name,
            boolean enabled,
            int order,
            BindingRuleKind kind,
            BindingActivators activators,
            String condition,
            String expression,
            BindingTarget target,
            String windowBucket,
            List<String> rollupBuckets
    ) {
        this(id, name, enabled, order, kind, activators, condition, expression, target, windowBucket, rollupBuckets, null, null, null, null);
    }

    public boolean isHistorian() {
        return kind == BindingRuleKind.HISTORIAN;
    }

    public boolean isReactive() {
        return kind != BindingRuleKind.HISTORIAN;
    }

    public boolean hasFormulaRef() {
        return formulaRef != null && !formulaRef.isBlank();
    }

    public BindingFormulaRef formulaLink() {
        return new BindingFormulaRef(formulaRef, formulaParams, formulaScope, formulaAppId);
    }

    public BindingRule withExpression(String nextExpression) {
        return new BindingRule(
                id,
                name,
                enabled,
                order,
                kind,
                activators,
                condition,
                nextExpression,
                target,
                windowBucket,
                rollupBuckets,
                formulaRef,
                formulaParams,
                formulaScope,
                formulaAppId
        );
    }
}
