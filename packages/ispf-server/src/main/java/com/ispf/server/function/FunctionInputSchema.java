package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.application.script.ScriptFieldCoercion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects function invoke input onto the descriptor {@code inputSchema}:
 * client schema is ignored, extra fields are dropped, required fields must be present,
 * values are coerced like script output.
 */
public final class FunctionInputSchema {

    private FunctionInputSchema() {
    }

    public static DataRecord apply(DataSchema schema, DataRecordPayloadRequest payload) {
        if (schema == null) {
            throw new IllegalArgumentException("input schema is required");
        }
        if (schema.fields().isEmpty()) {
            return DataRecord.empty(schema);
        }
        List<Map<String, Object>> sourceRows = rowsOf(payload);
        if (sourceRows.isEmpty()) {
            return DataRecord.single(schema, projectRow(schema, Map.of()));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : sourceRows) {
            rows.add(projectRow(schema, row));
        }
        return new DataRecord(schema, rows);
    }

    private static List<Map<String, Object>> rowsOf(DataRecordPayloadRequest payload) {
        if (payload == null || payload.rows() == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = payload.rows();
        if (rows.isEmpty() || rows.stream().allMatch(Map::isEmpty)) {
            return List.of();
        }
        return rows;
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
