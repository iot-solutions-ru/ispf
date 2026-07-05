package com.ispf.plugin.blueprint;

/**
 * Non-fatal warning when a model merge overwrites an existing member (last-wins).
 */
public record BlueprintMergeWarning(
        String kind,
        String name,
        String previousBlueprintId,
        String appliedBlueprintId
) {
    public static final String KIND_VARIABLE = "variable";
    public static final String KIND_EVENT = "event";
    public static final String KIND_FUNCTION = "function";
}
