package com.ispf.plugin.blueprint;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;

/**
 * Variable definition inside a model.
 */
public record BlueprintVariableDefinition(
        String name,
        String description,
        String group,
        DataSchema schema,
        boolean readable,
        boolean writable,
        DataRecord defaultValue,
        Boolean historyEnabled,
        Integer historyRetentionDays
) {
    public BlueprintVariableDefinition {
        if (description == null) {
            description = "";
        }
        if (group == null) {
            group = "default";
        }
        if (historyEnabled == null) {
            historyEnabled = false;
        }
    }

    public static BlueprintVariableDefinition of(
            String name,
            String description,
            String group,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord defaultValue
    ) {
        return of(name, description, group, schema, readable, writable, defaultValue, false, null);
    }

    public static BlueprintVariableDefinition of(
            String name,
            String description,
            String group,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord defaultValue,
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
        return new BlueprintVariableDefinition(
                name,
                description,
                group,
                schema,
                readable,
                writable,
                defaultValue,
                historyEnabled,
                historyRetentionDays
        );
    }

    /** Variable with history recording enabled (platform default retention). */
    public static BlueprintVariableDefinition withHistory(
            String name,
            String description,
            String group,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord defaultValue
    ) {
        return withHistory(name, description, group, schema, readable, writable, defaultValue, null);
    }

    public static BlueprintVariableDefinition withHistory(
            String name,
            String description,
            String group,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord defaultValue,
            Integer historyRetentionDays
    ) {
        return of(
                name,
                description,
                group,
                schema,
                readable,
                writable,
                defaultValue,
                true,
                historyRetentionDays
        );
    }
}
