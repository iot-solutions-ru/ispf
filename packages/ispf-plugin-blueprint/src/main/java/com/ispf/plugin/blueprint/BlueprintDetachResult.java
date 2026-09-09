package com.ispf.plugin.blueprint;

import java.util.List;

/**
 * Result of detaching a blueprint from an object (ADR-0058).
 */
public record BlueprintDetachResult(
        String blueprintId,
        String objectPath,
        List<String> removedVariables,
        List<String> removedEvents,
        List<String> removedFunctions,
        List<String> removedBindingRuleIds,
        List<String> skippedNames,
        boolean detached
) {
    public BlueprintDetachResult {
        removedVariables = removedVariables != null ? List.copyOf(removedVariables) : List.of();
        removedEvents = removedEvents != null ? List.copyOf(removedEvents) : List.of();
        removedFunctions = removedFunctions != null ? List.copyOf(removedFunctions) : List.of();
        removedBindingRuleIds = removedBindingRuleIds != null ? List.copyOf(removedBindingRuleIds) : List.of();
        skippedNames = skippedNames != null ? List.copyOf(skippedNames) : List.of();
    }
}
