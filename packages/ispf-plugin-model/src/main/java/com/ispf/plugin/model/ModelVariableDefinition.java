package com.ispf.plugin.model;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;

/**
 * Variable definition inside a model.
 */
public record ModelVariableDefinition(
        String name,
        String description,
        String group,
        DataSchema schema,
        boolean readable,
        boolean writable,
        String defaultBinding,
        DataRecord defaultValue,
        Boolean historyEnabled,
        Integer historyRetentionDays
) {
    public ModelVariableDefinition {
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

    public static ModelVariableDefinition of(
            String name,
            String description,
            String group,
            DataSchema schema,
            boolean readable,
            boolean writable,
            String defaultBinding,
            DataRecord defaultValue
    ) {
        return of(name, description, group, schema, readable, writable, defaultBinding, defaultValue, false, null);
    }

    public static ModelVariableDefinition of(
            String name,
            String description,
            String group,
            DataSchema schema,
            boolean readable,
            boolean writable,
            String defaultBinding,
            DataRecord defaultValue,
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
        return new ModelVariableDefinition(
                name,
                description,
                group,
                schema,
                readable,
                writable,
                defaultBinding,
                defaultValue,
                historyEnabled,
                historyRetentionDays
        );
    }

    /** Variable with history recording enabled (platform default retention). */
    public static ModelVariableDefinition withHistory(
            String name,
            String description,
            String group,
            DataSchema schema,
            boolean readable,
            boolean writable,
            String defaultBinding,
            DataRecord defaultValue
    ) {
        return withHistory(name, description, group, schema, readable, writable, defaultBinding, defaultValue, null);
    }

    public static ModelVariableDefinition withHistory(
            String name,
            String description,
            String group,
            DataSchema schema,
            boolean readable,
            boolean writable,
            String defaultBinding,
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
                defaultBinding,
                defaultValue,
                true,
                historyRetentionDays
        );
    }
}
