package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.ref.PlatformRef;

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

    /** First argument: bare ident, slash-ref, or dotted path. */
    static final String SOURCE_ARG = ".+?";

    /** Variable ref: quoted slash ref, bare ident, {@code @/name}, or dotted path slash form. */
    static final String VARIABLE_REF = "(?:\"[^\"]+\""
            + "|@/(?:fn|evt|tag)/[A-Za-z_][A-Za-z0-9_-]+"
            + "|@/(?:[A-Za-z_][A-Za-z0-9_]+(?:/[A-Za-z_][A-Za-z0-9_]+)?)"
            + "|[A-Za-z_][A-Za-z0-9_.-]+/[A-Za-z_][A-Za-z0-9_-]+"
            + "|" + IDENT + ")";

    static String unwrapQuotedRef(String raw) {
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private BindingSourceHelper() {
    }

    record SourceField(String sourceVariable, String field) {
    }

    static SourceField sourceField(String sourceVariable, String fieldGroup, String defaultField) {
        String field = fieldGroup != null ? fieldGroup : defaultField;
        return new SourceField(sourceVariable, field);
    }

    static PlatformRef resolveVariableSource(String sourceArg, String fieldOverride) {
        PlatformRef ref = PlatformRefValueHelper.parseVariableArg(sourceArg.trim());
        if (fieldOverride != null && !fieldOverride.isBlank()) {
            ref = PlatformRef.variable(ref.object(), ref.name(), fieldOverride);
        }
        return ref;
    }

    static Optional<Double> readNumericSource(
            PlatformObject object,
            String sourceArg,
            String fieldOverride,
            BindingEvaluationContext context
    ) {
        return PlatformRefValueHelper.readNumericVariable(
                object,
                resolveVariableSource(sourceArg, fieldOverride),
                context
        );
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
