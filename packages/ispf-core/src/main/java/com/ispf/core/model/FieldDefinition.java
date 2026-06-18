package com.ispf.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Describes a single column in a {@link DataRecord} schema.
 */
public record FieldDefinition(
        String name,
        FieldType type,
        String description,
        boolean nullable,
        DataSchema nestedSchema
) {
    public FieldDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (description == null) {
            description = "";
        }
        if ((type == FieldType.RECORD || type == FieldType.RECORD_LIST) && nestedSchema == null) {
            throw new IllegalArgumentException("nestedSchema required for RECORD types: " + name);
        }
    }

    @JsonCreator
    public static FieldDefinition create(
            @JsonProperty("name") String name,
            @JsonProperty("type") FieldType type,
            @JsonProperty("description") String description,
            @JsonProperty("nullable") Boolean nullable,
            @JsonProperty("nestedSchema") DataSchema nestedSchema
    ) {
        return new FieldDefinition(name, type, description, nullable != null && nullable, nestedSchema);
    }

    public static FieldDefinition of(String name, FieldType type) {
        return new FieldDefinition(name, type, "", true, null);
    }

    public static FieldDefinition required(String name, FieldType type) {
        return new FieldDefinition(name, type, "", false, null);
    }
}
