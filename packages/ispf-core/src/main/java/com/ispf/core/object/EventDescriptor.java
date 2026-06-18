package com.ispf.core.object;

import com.ispf.core.model.DataSchema;

/**
 * Metadata for an event that an object can emit.
 */
public record EventDescriptor(
        String name,
        String description,
        DataSchema payloadSchema,
        EventLevel level
) {
}
