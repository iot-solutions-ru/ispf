package com.ispf.core.object;

import com.ispf.core.model.DataSchema;

/**
 * Metadata for a callable function on an object.
 * Script functions (Phase 14) include {@code sourceBody} and optional {@code dataSourcePath}.
 */
public record FunctionDescriptor(
        String name,
        String description,
        DataSchema inputSchema,
        DataSchema outputSchema,
        String sourceType,
        String sourceBody,
        String dataSourcePath,
        String version
) {
    public FunctionDescriptor(String name, String description, DataSchema inputSchema, DataSchema outputSchema) {
        this(name, description, inputSchema, outputSchema, null, null, null, null);
    }

    public boolean hasScriptBody() {
        return sourceBody != null && !sourceBody.isBlank() && !hasJavaBody();
    }

    public boolean hasJavaBody() {
        return sourceBody != null
                && !sourceBody.isBlank()
                && sourceType != null
                && "java".equalsIgnoreCase(sourceType.trim());
    }
}
