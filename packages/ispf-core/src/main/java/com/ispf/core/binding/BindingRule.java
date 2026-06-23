package com.ispf.core.binding;

/**
 * Declarative binding: activator (when) → condition (if) → expression (how) → target (where).
 */
public record BindingRule(
        String id,
        String name,
        boolean enabled,
        int order,
        BindingActivators activators,
        String condition,
        String expression,
        BindingTarget target
) {
    public BindingRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Binding rule id is required");
        }
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Binding rule expression is required");
        }
        if (target == null || target.variableName() == null || target.variableName().isBlank()) {
            throw new IllegalArgumentException("Binding rule target is required");
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
    }
}
