package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Evaluates a single binding expression and maps the result to a target variable schema.
 */
public class BindingExpressionEvaluator {

    private final ExpressionEngine engine = new ExpressionEngine();

    public Optional<DataRecord> evaluate(
            PlatformObject platformObject,
            String targetVariableName,
            String expression,
            DataSchema targetSchema,
            BindingEvaluationContext context
    ) {
        if (expression == null || expression.isBlank()) {
            return Optional.empty();
        }
        try {
            var platformBinding = PlatformBindingRegistry.find(expression);
            if (platformBinding.isPresent()) {
                return platformBinding.get()
                        .evaluate(platformObject, targetVariableName, expression, context)
                        .map(result -> toDataRecord(targetSchema, result));
            }
            Object result = engine.evaluate(expression, platformObject);
            return Optional.of(toDataRecord(targetSchema, result));
        } catch (ExpressionException ignored) {
            return Optional.empty();
        }
    }

    public static boolean recordsEqual(DataRecord left, DataRecord right) {
        if (left == null || right == null) {
            return left == right;
        }
        return Objects.equals(left.firstRow(), right.firstRow());
    }

    private static DataRecord toDataRecord(DataSchema schema, Object result) {
        if (schema.fieldCount() == 1) {
            String fieldName = schema.fields().getFirst().name();
            return DataRecord.single(schema, Map.of(fieldName, coerce(result, schema.fields().getFirst())));
        }
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            for (FieldDefinition field : schema.fields()) {
                row.put(field.name(), coerce(map.get(field.name()), field));
            }
            return DataRecord.single(schema, row);
        }
        if (isScalarResult(result)) {
            return scalarToRecord(schema, result);
        }
        throw new ExpressionException("Cannot map expression result to schema: " + schema.name());
    }

    private static boolean isScalarResult(Object result) {
        return result instanceof Number || result instanceof Boolean || result instanceof String;
    }

    private static DataRecord scalarToRecord(DataSchema schema, Object result) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        FieldDefinition primary = schema.fields().stream()
                .filter(field -> "value".equals(field.name()))
                .findFirst()
                .orElse(schema.fields().getFirst());
        for (FieldDefinition field : schema.fields()) {
            if (field.name().equals(primary.name())) {
                row.put(field.name(), coerce(result, field));
            } else if ("raw".equals(field.name()) && result instanceof Number number) {
                row.put(field.name(), String.valueOf(number.doubleValue()));
            } else if (field.type() == FieldType.STRING) {
                row.put(field.name(), "");
            } else {
                row.put(field.name(), null);
            }
        }
        return DataRecord.single(schema, row);
    }

    private static Object coerce(Object value, FieldDefinition field) {
        if (value == null) {
            return null;
        }
        return switch (field.type()) {
            case BOOLEAN -> Boolean.valueOf(value.toString());
            case INTEGER -> ((Number) value).intValue();
            case LONG -> ((Number) value).longValue();
            case DOUBLE -> ((Number) value).doubleValue();
            case STRING -> value.toString();
            default -> value;
        };
    }
}
