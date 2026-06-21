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
        return evaluateBindingsReturningChanges(platformObject, BindingEvaluationContext.NONE);
    }

    public List<String> evaluateBindingsReturningChanges(
            PlatformObject platformObject,
            BindingEvaluationContext context
    ) {
        List<String> changed = new ArrayList<>();
        for (Variable variable : platformObject.variables().values()) {
            variable.bindingExpression().ifPresent(expr -> {
                try {
                    var platformBinding = PlatformBindingRegistry.find(expr);
                    if (platformBinding.isPresent()) {
                        platformBinding.get().evaluate(platformObject, variable.name(), expr, context).ifPresent(result -> {
                            DataRecord record = toDataRecord(variable.schema(), result);
                            applyIfChanged(variable, record, changed);
                        });
                        return;
                    }
                    Object result = engine.evaluate(expr, platformObject);
                    DataRecord record = toDataRecord(variable.schema(), result);
                    applyIfChanged(variable, record, changed);
                } catch (ExpressionException ignored) {
                    // Binding stays at default value until dependencies are available
                }
            });
        }
        return changed;
    }

    private static void applyIfChanged(Variable variable, DataRecord record, List<String> changed) {
        DataRecord previous = variable.value().orElse(null);
        if (!recordsEqual(previous, record)) {
            variable.setComputedValue(record);
            changed.add(variable.name());
        }
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
        if (isScalarResult(result)) {
            return scalarToRecord(schema, result);
        }
        throw new ExpressionException("Cannot map expression result to schema: " + schema.name());
    }

    private static boolean isScalarResult(Object result) {
        return result instanceof Number || result instanceof Boolean || result instanceof String;
    }

    /**
     * Maps a scalar binding result (e.g. counterRate B/s) into multi-field schemas such as snmpNumeric.
     */
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
