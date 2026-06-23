package com.ispf.core.binding;

import java.util.List;

/**
 * When a binding rule is evaluated.
 */
public record BindingActivators(
        boolean onStartup,
        List<BindingVariableRef> onVariableChange,
        String onEvent,
        long periodicMs
) {
    public BindingActivators {
        onVariableChange = onVariableChange != null ? List.copyOf(onVariableChange) : List.of();
    }

    public static BindingActivators onLocalChange() {
        return new BindingActivators(false, List.of(BindingVariableRef.localAny()), null, 0);
    }

    public static BindingActivators onRemoteChange(String objectPath, String variableName) {
        return new BindingActivators(false, List.of(BindingVariableRef.remote(objectPath, variableName)), null, 0);
    }

    public boolean matchesVariableChange(String ruleObjectPath, String changedObjectPath, String changedVariable) {
        if (onVariableChange.isEmpty()) {
            return false;
        }
        return onVariableChange.stream()
                .anyMatch(ref -> ref.matches(ruleObjectPath, changedObjectPath, changedVariable));
    }
}
