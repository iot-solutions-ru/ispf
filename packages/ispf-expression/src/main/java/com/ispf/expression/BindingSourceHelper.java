package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Shared parsing and source-variable access for platform bindings.
 */
final class BindingSourceHelper {

    static final String IDENT = "[A-Za-z_][A-Za-z0-9_]*";
    static final Pattern IDENT_PATTERN = Pattern.compile(IDENT);
    static final String NUMERIC = "[-+]?\\d+(?:\\.\\d+)?";
    static final String QUOTED_STRING = "\"([^\"]*)\"";

    private BindingSourceHelper() {
    }

    record SourceField(String sourceVariable, String field) {
    }

    static SourceField sourceField(String sourceVariable, String fieldGroup, String defaultField) {
        String field = fieldGroup != null ? fieldGroup : defaultField;
        return new SourceField(sourceVariable, field);
    }

    static String stateKey(PlatformObject object, String targetVariable) {
        return object.path() + "|" + targetVariable;
    }

    static Optional<DataRecord> readSourceRecord(PlatformObject object, String sourceVariable) {
        Variable source = object.getVariable(sourceVariable)
                .orElseThrow(() -> new ExpressionException("source not found: " + sourceVariable));
        Optional<DataRecord> record = source.value();
        if (record.isEmpty() || record.get().rowCount() == 0) {
            return Optional.empty();
        }
        return record;
    }

    static Optional<Object> readField(PlatformObject object, String sourceVariable, String field) {
        return readSourceRecord(object, sourceVariable)
                .map(record -> record.firstRow().get(field))
                .filter(value -> value != null);
    }

    static Optional<Double> readNumericField(PlatformObject object, String sourceVariable, String field) {
        return readField(object, sourceVariable, field)
                .filter(Number.class::isInstance)
                .map(value -> ((Number) value).doubleValue());
    }
}
