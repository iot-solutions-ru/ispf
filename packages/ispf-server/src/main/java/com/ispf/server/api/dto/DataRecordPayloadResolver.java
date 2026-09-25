package com.ispf.server.api.dto;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;

import java.util.List;
import java.util.Map;

public final class DataRecordPayloadResolver {

    private DataRecordPayloadResolver() {
    }

    public static DataRecord resolve(DataSchema contractSchema, DataRecordPayloadRequest payload) {
        DataSchema schema = contractSchema != null
                ? contractSchema
                : DataSchema.builder("void").build();
        if (payload == null) {
            return emptyAgainstContract(schema);
        }
        // Contract schema wins: client schema must not replace field names or types.
        List<Map<String, Object>> rows = payload.rows();
        if (rows == null || rows.isEmpty() || rows.stream().allMatch(Map::isEmpty)) {
            return emptyAgainstContract(schema);
        }
        return new DataRecord(schema, rows);
    }

    private static DataRecord emptyAgainstContract(DataSchema schema) {
        if (schema.fields().isEmpty()) {
            return DataRecord.empty(schema);
        }
        // Enforce required fields when the caller sent no values.
        return DataRecord.single(schema, Map.of());
    }

    public static DataRecordPayloadRequest fromRecord(DataRecord record) {
        if (record == null) {
            return null;
        }
        return new DataRecordPayloadRequest(record.schema(), record.rows());
    }
}
