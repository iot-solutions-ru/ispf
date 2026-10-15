package com.ispf.server.event;

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
 * Projects a fired event payload onto the event descriptor schema.
 * The schema in the request body is ignored. Extra fields are dropped.
 * A required field with no value is a call error.
 */
public final class EventPayloadSchema {

    private EventPayloadSchema() {
    }

    public static DataRecord apply(DataSchema schema, DataRecordPayloadRequest payload) {
        DataSchema contract = schema != null ? schema : DataSchema.builder("eventPayload").build();
        if (contract.fields().isEmpty()) {
            return DataRecord.empty(contract);
        }
        List<Map<String, Object>> sourceRows = rowsOf(payload);
        if (sourceRows.isEmpty()) {
            requirePresent(contract, Map.of());
            return DataRecord.empty(contract);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : sourceRows) {
            rows.add(projectRow(contract, row));
        }
        return new DataRecord(contract, rows);
    }

    private static List<Map<String, Object>> rowsOf(DataRecordPayloadRequest payload) {
        if (payload == null || payload.rows() == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = payload.rows();
        if (rows.isEmpty() || rows.stream().allMatch(row -> row == null || row.isEmpty())) {
            return List.of();
        }
        return rows;
    }

    private static Map<String, Object> projectRow(DataSchema schema, Map<String, Object> source) {
        Map<String, Object> values = source != null ? source : Map.of();
        requirePresent(schema, values);
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (FieldDefinition field : schema.fields()) {
            normalized.put(field.name(), ScriptFieldCoercion.coerce(field, values.get(field.name())));
        }
        return normalized;
    }

    private static void requirePresent(DataSchema schema, Map<String, Object> values) {
        for (FieldDefinition field : schema.fields()) {
            if (field.nullable()) {
                continue;
            }
            if (!values.containsKey(field.name()) || values.get(field.name()) == null) {
                throw new IllegalArgumentException(
                        "Event payload missing required field '" + field.name() + "'"
                );
            }
        }
    }
}
