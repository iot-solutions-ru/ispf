package com.ispf.plugin.blueprint;

import java.util.List;

public record BlueprintApplyResult(
        BlueprintAttachment attachment,
        List<BlueprintMergeWarning> warnings
) {
    public BlueprintApplyResult {
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}
