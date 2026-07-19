package com.ispf.core.object;

import com.ispf.core.model.DataSchema;

import java.util.List;

/**
 * Metadata for an event that an object can emit.
 * Optional {@link #invokeRoles()} restricts who may fire the event (empty = object INVOKE ACL only).
 */
public record EventDescriptor(
        String name,
        String description,
        DataSchema payloadSchema,
        EventLevel level,
        List<String> invokeRoles
) {
    public EventDescriptor {
        invokeRoles = invokeRoles != null ? List.copyOf(invokeRoles) : List.of();
    }

    public EventDescriptor(String name, String description, DataSchema payloadSchema, EventLevel level) {
        this(name, description, payloadSchema, level, List.of());
    }
}
