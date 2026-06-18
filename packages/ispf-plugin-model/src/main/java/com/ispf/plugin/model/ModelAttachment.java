package com.ispf.plugin.model;

import java.time.Instant;

/**
 * Records that a model has been applied to an object path.
 */
public record ModelAttachment(
        String id,
        String modelId,
        String modelName,
        ModelType modelType,
        String objectPath,
        Instant attachedAt
) {
}
