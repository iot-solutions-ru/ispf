package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;

import java.util.Optional;

/**
 * Reads variable values via {@link PlatformRef} for platform bindings.
 */
final class PlatformRefValueHelper {

    private PlatformRefValueHelper() {
    }

    static PlatformRef parseVariableArg(String raw) {
        return PlatformRefParser.parseVariableSource(raw);
    }

    static Optional<Object> readVariable(
            PlatformObject object,
            PlatformRef ref,
            BindingEvaluationContext context
    ) {
        if (ref.isCurrentObject() || ref.object().equals(object.path())) {
            return BindingSourceHelper.readField(object, ref.name(), ref.field());
        }
        return context.readRemoteField(ref.object(), ref.name(), ref.field());
    }

    static Optional<Double> readNumericVariable(
            PlatformObject object,
            PlatformRef ref,
            BindingEvaluationContext context
    ) {
        return readVariable(object, ref, context)
                .filter(Number.class::isInstance)
                .map(value -> ((Number) value).doubleValue());
    }

    static DataRecord readInputRecord(PlatformObject object, String sourceVariable) {
        PlatformRef ref = PlatformRefParser.parseVariableSource(sourceVariable);
        return BindingSourceHelper.readSourceRecord(object, ref.name())
                .orElse(DataRecord.empty(
                        object.getVariable(ref.name())
                                .map(v -> v.schema())
                                .orElseThrow(() -> new ExpressionException("source not found: " + sourceVariable))
                ));
    }
}
