package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.server.application.script.ScriptFieldCoercion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects a function result onto the declared output schema: extra fields are dropped,
 * required fields must be present, and numbers are coerced the same way as script output.
 */
public final class FunctionOutputSchema {

    private FunctionOutputSchema() {
    }

    public static DataRecord apply(DataSchema schema, DataRecord raw) {
        if (schema == null) {
            throw new IllegalArgumentException("output schema is required");
        }
        if (raw == null) {
            throw new IllegalArgumentException("function output is required");
        }
        if (raw.rowCount() == 0) {
            return DataRecord.single(schema, projectRow(schema, Map.of()));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : raw.rows()) {
            rows.add(projectRow(schema, row));
        }
        return new DataRecord(schema, rows);
    }

    private static Map<String, Object> projectRow(DataSchema schema, Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        Map<String, Object> values = source != null ? source : Map.of();
        for (FieldDefinition field : schema.fields()) {
            Object value = values.get(field.name());
            if (value == null && !field.nullable()) {
                throw new IllegalArgumentException("Required field missing: " + field.name());
            }
            normalized.put(field.name(), ScriptFieldCoercion.coerce(field, value));
        }
        return normalized;
    }
}
