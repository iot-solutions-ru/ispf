package com.ispf.server.api.dto;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;

import java.util.List;
import java.util.Map;

public final class DataRecordPayloadResolver {

    private DataRecordPayloadResolver() {
    }

    public static DataRecord resolve(DataSchema defaultSchema, DataRecordPayloadRequest payload) {
        if (payload == null) {
            return DataRecord.empty(defaultSchema);
        }
        DataSchema schema = payload.schema() != null && !payload.schema().fields().isEmpty()
                ? payload.schema()
                : defaultSchema;
        List<Map<String, Object>> rows = payload.rows();
        if (rows == null || rows.isEmpty() || rows.stream().allMatch(Map::isEmpty)) {
            return DataRecord.empty(schema);
        }
        return new DataRecord(schema, rows);
    }

    public static DataRecordPayloadRequest fromRecord(DataRecord record) {
        if (record == null) {
            return null;
        }
        return new DataRecordPayloadRequest(record.schema(), record.rows());
    }
}
