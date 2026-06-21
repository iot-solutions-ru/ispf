package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class BindingResultHelper {

    private BindingResultHelper() {
    }

    static Optional<Object> mapFunctionOutput(
            PlatformObject object,
            String targetVariable,
            DataRecord output
    ) {
        if (output == null || output.rowCount() == 0) {
            return Optional.empty();
        }
        Map<String, Object> row = output.firstRow();
        Variable target = object.getVariable(targetVariable).orElse(null);
        if (target == null) {
            return scalarFromRow(row);
        }
        DataSchema schema = target.schema();
        if (schema.fieldCount() == 1) {
            String fieldName = schema.fields().getFirst().name();
            Object value = row.get(fieldName);
            if (value != null) {
                return Optional.of(value);
            }
        }
        if (row.size() == 1) {
            return Optional.of(row.values().iterator().next());
        }
        if (row.containsKey("value") && row.get("value") != null && schema.fieldCount() > 1) {
            return Optional.of(row.get("value"));
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (var field : schema.fields()) {
            if (row.containsKey(field.name())) {
                mapped.put(field.name(), row.get(field.name()));
            }
        }
        if (!mapped.isEmpty()) {
            return Optional.of(mapped);
        }
        return scalarFromRow(row);
    }

    private static Optional<Object> scalarFromRow(Map<String, Object> row) {
        if (row.containsKey("value") && row.get("value") != null) {
            return Optional.of(row.get("value"));
        }
        if (row.size() == 1) {
            return Optional.of(row.values().iterator().next());
        }
        return Optional.of(row);
    }

    static DataRecord buildFunctionInput(PlatformObject object, String sourceVariable, String field) {
        return BindingSourceHelper.readSourceRecord(object, sourceVariable)
                .map(record -> {
                    Object value = record.firstRow().get(field);
                    Map<String, Object> input = new LinkedHashMap<>();
                    input.put(field, value);
                    input.put("value", value);
                    return DataRecord.single(record.schema(), input);
                })
                .orElse(DataRecord.empty(
                        object.getVariable(sourceVariable)
                                .map(Variable::schema)
                                .orElseThrow(() -> new ExpressionException("source not found: " + sourceVariable))
                ));
    }
}
