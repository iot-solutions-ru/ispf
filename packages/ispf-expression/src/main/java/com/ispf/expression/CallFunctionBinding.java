package com.ispf.expression;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code callFunction(functionName)} or
 * {@code callFunction(functionName, sourceVariable[, field])}.
 */
public final class CallFunctionBinding implements PlatformBinding {

    static final CallFunctionBinding INSTANCE = new CallFunctionBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "callFunction\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*(" + BindingSourceHelper.IDENT + ")\\s*)?)?\\)"
    );

    private CallFunctionBinding() {
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
        String functionName = matcher.group(1);
        String sourceVariable = matcher.group(2);

        DataRecord input;
        if (sourceVariable != null) {
            input = BindingSourceHelper.readSourceRecord(object, sourceVariable)
                    .orElse(DataRecord.empty(
                            object.getVariable(sourceVariable)
                                    .map(v -> v.schema())
                                    .orElseThrow(() -> new ExpressionException("source not found: " + sourceVariable))
                    ));
        } else {
            var descriptor = object.functions().get(functionName);
            input = DataRecord.empty(
                    descriptor != null
                            ? descriptor.inputSchema()
                            : DataSchema.builder("voidInput").build()
            );
        }

        return context.invokeFunction(object.path(), functionName, input)
                .flatMap(output -> BindingResultHelper.mapFunctionOutput(object, targetVariable, output));
    }
}
