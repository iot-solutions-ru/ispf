package com.ispf.core.object;

import com.ispf.core.model.DataSchema;

import java.util.List;

/**
 * Metadata for a callable function on an object.
 * Script functions (Phase 14) include {@code sourceBody} and optional {@code dataSourcePath}.
 * Optional {@link #invokeRoles()} restricts who may invoke (empty = object INVOKE ACL only).
 */
public record FunctionDescriptor(
        String name,
        String description,
        DataSchema inputSchema,
        DataSchema outputSchema,
        String sourceType,
        String sourceBody,
        String dataSourcePath,
        String version,
        List<String> invokeRoles
) {
    public FunctionDescriptor {
        invokeRoles = invokeRoles != null ? List.copyOf(invokeRoles) : List.of();
    }

    public FunctionDescriptor(String name, String description, DataSchema inputSchema, DataSchema outputSchema) {
        this(name, description, inputSchema, outputSchema, null, null, null, null, List.of());
    }

    public FunctionDescriptor(
            String name,
            String description,
            DataSchema inputSchema,
            DataSchema outputSchema,
            String sourceType,
            String sourceBody,
            String dataSourcePath,
            String version
    ) {
        this(name, description, inputSchema, outputSchema, sourceType, sourceBody, dataSourcePath, version, List.of());
    }

    public boolean hasExpressionBody() {
        return sourceType != null
                && "expression".equalsIgnoreCase(sourceType.trim())
                && sourceBody != null
                && !sourceBody.isBlank();
    }

    public boolean hasObjectQueryBody() {
        return sourceType != null
                && "object-query".equalsIgnoreCase(sourceType.trim())
                && sourceBody != null
                && !sourceBody.isBlank();
    }

    public boolean hasScriptBody() {
        return sourceBody != null
                && !sourceBody.isBlank()
                && !hasJavaBody()
                && !hasPulseBody()
                && !hasExpressionBody()
                && !hasObjectQueryBody();
    }

    public boolean hasPulseBody() {
        return sourceType != null
                && "pulse".equalsIgnoreCase(sourceType.trim())
                && sourceBody != null
                && !sourceBody.isBlank();
    }

    public boolean hasJavaBody() {
        return sourceBody != null
                && !sourceBody.isBlank()
                && sourceType != null
                && "java".equalsIgnoreCase(sourceType.trim());
    }
}
