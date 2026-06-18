package com.ispf.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Schema definition for tabular/structured data.
 */
public final class DataSchema {

    private final String name;
    private final List<FieldDefinition> fields;
    private final Map<String, FieldDefinition> fieldIndex;

    @JsonCreator
    public DataSchema(
            @JsonProperty("name") String name,
            @JsonProperty("fields") List<FieldDefinition> fields
    ) {
        this.name = name != null ? name : "anonymous";
        this.fields = List.copyOf(fields != null ? fields : List.of());
        Map<String, FieldDefinition> index = new LinkedHashMap<>();
        for (FieldDefinition field : this.fields) {
            if (index.putIfAbsent(field.name(), field) != null) {
                throw new IllegalArgumentException("Duplicate field: " + field.name());
            }
        }
        this.fieldIndex = Collections.unmodifiableMap(index);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("fields")
    public List<FieldDefinition> fields() {
        return fields;
    }

    public Optional<FieldDefinition> field(String name) {
        return Optional.ofNullable(fieldIndex.get(name));
    }

    public int fieldCount() {
        return fields.size();
    }

    public static final class Builder {
        private final String name;
        private final List<FieldDefinition> fields = new ArrayList<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public Builder field(FieldDefinition field) {
            fields.add(field);
            return this;
        }

        public Builder field(String name, FieldType type) {
            return field(FieldDefinition.of(name, type));
        }

        public DataSchema build() {
            return new DataSchema(name, fields);
        }
    }
}
