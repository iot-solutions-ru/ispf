package com.ispf.plugin.blueprint;

import java.time.Instant;

/**
 * Records that a model has been applied to an object path.
 */
public record BlueprintAttachment(
        String id,
        String blueprintId,
        String blueprintName,
        BlueprintType blueprintType,
        String objectPath,
        Instant attachedAt
) {
}
