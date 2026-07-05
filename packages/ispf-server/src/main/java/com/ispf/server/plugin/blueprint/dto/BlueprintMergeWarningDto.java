package com.ispf.server.plugin.blueprint.dto;

import com.ispf.plugin.blueprint.BlueprintMergeWarning;

public record BlueprintMergeWarningDto(
        String kind,
        String name,
        String previousBlueprintId,
        String appliedBlueprintId
) {
    public static BlueprintMergeWarningDto from(BlueprintMergeWarning warning) {
        return new BlueprintMergeWarningDto(
                warning.kind(),
                warning.name(),
                warning.previousBlueprintId(),
                warning.appliedBlueprintId()
        );
    }
}
