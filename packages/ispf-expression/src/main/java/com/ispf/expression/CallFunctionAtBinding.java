package com.ispf.expression;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code callFunctionAt("objectPath", functionName)} or
 * {@code callFunctionAt("objectPath", functionName, sourceVariable[, field])}.
 */
public final class CallFunctionAtBinding implements PlatformBinding {

    static final CallFunctionAtBinding INSTANCE = new CallFunctionAtBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "callFunctionAt\\(\\s*" + BindingSourceHelper.QUOTED_STRING + "\\s*,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*(" + BindingSourceHelper.IDENT
                    + ")\\s*(?:,\\s*(" + BindingSourceHelper.IDENT + ")\\s*)?)?\\)"
    );

    private CallFunctionAtBinding() {
    }

    @Override
    public boolean matches(String expression) {
        return expression != null && PATTERN.matcher(expression.trim()).matches();
    }

    @Override
    public Optional<Object> evaluate(
            PlatformObject object,
            String targetVariable,
            String expression,
            BindingEvaluationContext context
    ) {
        Matcher matcher = PATTERN.matcher(expression.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String remotePath = matcher.group(1);
        String functionName = matcher.group(2);
        String sourceVariable = matcher.group(3);

        DataRecord input;
        if (sourceVariable != null) {
            input = BindingSourceHelper.readSourceRecord(object, sourceVariable)
                    .orElse(DataRecord.empty(
                            object.getVariable(sourceVariable)
                                    .map(v -> v.schema())
                                    .orElseThrow(() -> new ExpressionException("source not found: " + sourceVariable))
                    ));
        } else {
            input = DataRecord.empty(DataSchema.builder("voidInput").build());
        }

        return context.invokeFunction(remotePath, functionName, input)
                .flatMap(output -> BindingResultHelper.mapFunctionOutput(object, targetVariable, output));
    }
}
