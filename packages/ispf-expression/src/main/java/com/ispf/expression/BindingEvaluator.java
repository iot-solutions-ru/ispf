package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates binding expressions and writes computed values back to object variables.
 */
public class BindingEvaluator {

    private final ExpressionEngine engine = new ExpressionEngine();

    public void evaluateBindings(PlatformObject platformObject) {
        evaluateBindingsReturningChanges(platformObject);
    }

    /**
     * Re-evaluates all binding expressions and returns variable names whose values changed.
     */
    public List<String> evaluateBindingsReturningChanges(PlatformObject platformObject) {
        List<String> changed = new ArrayList<>();
        for (Variable variable : platformObject.variables().values()) {
            variable.bindingExpression().ifPresent(expr -> {
                try {
                    Object result = engine.evaluate(expr, platformObject);
                    DataRecord record = toDataRecord(variable.schema(), result);
                    DataRecord previous = variable.value().orElse(null);
                    if (!recordsEqual(previous, record)) {
                        variable.setComputedValue(record);
                        changed.add(variable.name());
                    }
                } catch (ExpressionException ignored) {
                    // Binding stays at default value until dependencies are available
                }
            });
        }
        return changed;
    }

    private static boolean recordsEqual(DataRecord left, DataRecord right) {
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
        throw new ExpressionException("Cannot map expression result to schema: " + schema.name());
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
