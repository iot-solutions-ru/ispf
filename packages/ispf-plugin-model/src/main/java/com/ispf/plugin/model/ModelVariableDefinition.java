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
        DataRecord defaultValue
) {
    public ModelVariableDefinition {
        if (description == null) {
            description = "";
        }
        if (group == null) {
            group = "default";
        }
    }
}
