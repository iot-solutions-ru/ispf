package com.ispf.core.object;

import com.ispf.core.model.DataSchema;

/**
 * Metadata for a callable function on an object.
 */
public record FunctionDescriptor(
        String name,
        String description,
        DataSchema inputSchema,
        DataSchema outputSchema
) {
}
