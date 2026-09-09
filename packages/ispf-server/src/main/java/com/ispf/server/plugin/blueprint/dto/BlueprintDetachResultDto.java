package com.ispf.server.plugin.blueprint.dto;

import com.ispf.plugin.blueprint.BlueprintDetachResult;

import java.util.List;

public record BlueprintDetachResultDto(
        String blueprintId,
        String objectPath,
        List<String> removedVariables,
        List<String> removedEvents,
        List<String> removedFunctions,
        List<String> removedBindingRuleIds,
        List<String> skippedNames,
        boolean detached
) {
    public static BlueprintDetachResultDto from(BlueprintDetachResult result) {
        return new BlueprintDetachResultDto(
                result.blueprintId(),
                result.objectPath(),
                result.removedVariables(),
                result.removedEvents(),
                result.removedFunctions(),
                result.removedBindingRuleIds(),
                result.skippedNames(),
                result.detached()
        );
    }
}
