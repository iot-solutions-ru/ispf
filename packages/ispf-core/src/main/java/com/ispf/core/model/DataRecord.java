package com.ispf.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A typed data container with schema validation — ISPF DataRecord (DataTable equivalent).
 */
public final class DataRecord {

    private final DataSchema schema;
    private final List<Map<String, Object>> rows;

    @JsonCreator
    public DataRecord(
            @JsonProperty("schema") DataSchema schema,
            @JsonProperty("rows") List<Map<String, Object>> rows
    ) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.rows = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                addRow(row);
            }
        }
    }

    public static DataRecord empty(DataSchema schema) {
        return new DataRecord(schema, List.of());
    }

    public static DataRecord single(DataSchema schema, Map<String, Object> values) {
        DataRecord record = new DataRecord(schema, List.of());
        record.addRow(values);
        return record;
    }

    @JsonProperty("schema")
    public DataSchema schema() {
        return schema;
    }

    @JsonProperty("rows")
    public List<Map<String, Object>> rows() {
        return Collections.unmodifiableList(rows);
    }

    public int rowCount() {
        return rows.size();
    }

    public Map<String, Object> firstRow() {
        if (rows.isEmpty()) {
            throw new IllegalStateException("DataRecord has no rows");
        }
        return rows.getFirst();
    }

    public Object get(String field, int rowIndex) {
        return rows.get(rowIndex).get(field);
    }

    public void addRow(Map<String, Object> values) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (FieldDefinition field : schema.fields()) {
            Object value = values.get(field.name());
            if (value == null && !field.nullable()) {
                throw new IllegalArgumentException("Required field missing: " + field.name());
            }
            if (value != null) {
                value = storedValue(field, value);
            }
            normalized.put(field.name(), value);
        }
        rows.add(Collections.unmodifiableMap(normalized));
    }

    private static Object storedValue(FieldDefinition field, Object value) {
        if (field.type() == FieldType.DOUBLE) {
            return finiteDouble(field, value);
        }
        if (field.type() == FieldType.INTEGER) {
            return IntegerValues.requireInt(field.name(), value);
        }
        validateType(field, value);
        return value;
    }

    private static Double finiteDouble(FieldDefinition field, Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field.name() + " must be double");
        }
        double coerced = number.doubleValue();
        if (!Double.isFinite(coerced)) {
            throw new IllegalArgumentException(field.name() + " must be double");
        }
        return coerced;
    }

    private static void validateType(FieldDefinition field, Object value) {
        switch (field.type()) {
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException(field.name() + " must be boolean");
                }
            }
            case INTEGER -> {
                if (!(value instanceof Integer)) {
                    throw new IllegalArgumentException(field.name() + " must be integer");
                }
            }
            case LONG -> {
                if (!(value instanceof Long) && !(value instanceof Integer)) {
                    throw new IllegalArgumentException(field.name() + " must be long");
                }
            }
            case DOUBLE -> throw new IllegalStateException("double is stored by finiteDouble");
            case STRING -> {
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException(field.name() + " must be string");
                }
            }
            case DATETIME -> {
                if (!(value instanceof Instant) && !(value instanceof String)) {
                    throw new IllegalArgumentException(field.name() + " must be datetime");
                }
            }
            case RECORD -> {
                if (!(value instanceof DataRecord)) {
                    throw new IllegalArgumentException(field.name() + " must be DataRecord");
                }
            }
            case RECORD_LIST -> {
                if (!(value instanceof List)) {
                    throw new IllegalArgumentException(field.name() + " must be list");
                }
            }
            case BINARY -> {
                if (!(value instanceof byte[])) {
                    throw new IllegalArgumentException(field.name() + " must be binary");
                }
            }
        }
    }
}
