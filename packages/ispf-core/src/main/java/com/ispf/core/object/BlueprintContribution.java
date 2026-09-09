package com.ispf.core.object;

import java.util.List;

/**
 * Names contributed by one applied blueprint (ADR-0058 ownership manifest).
 */
public record BlueprintContribution(
        List<String> variables,
        List<String> events,
        List<String> functions,
        List<String> bindingRuleIds
) {
    public BlueprintContribution {
        variables = variables != null ? List.copyOf(variables) : List.of();
        events = events != null ? List.copyOf(events) : List.of();
        functions = functions != null ? List.copyOf(functions) : List.of();
        bindingRuleIds = bindingRuleIds != null ? List.copyOf(bindingRuleIds) : List.of();
    }

    public static BlueprintContribution empty() {
        return new BlueprintContribution(List.of(), List.of(), List.of(), List.of());
    }
}
