package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code call(<functionRef>[, <inputRef>])}.
 */
public final class CallRefBinding implements PlatformBinding {

    static final CallRefBinding INSTANCE = new CallRefBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "call\\(\\s*([^,)]+)\\s*(?:,\\s*([^)]+)\\s*)?\\)",
            Pattern.CASE_INSENSITIVE
    );

    private CallRefBinding() {
    }

    @Override
    public boolean matches(String expression) {
        if (expression == null) {
            return false;
        }
        String trimmed = expression.trim();
        if (!trimmed.toLowerCase().startsWith("call(")) {
            return false;
        }
        Matcher matcher = PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return false;
        }
        return PlatformRefParser.parseOptional(matcher.group(1).trim())
                .map(PlatformRef::isFunction)
                .orElse(false);
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
        PlatformRef fnRef = PlatformRefParser.parse(matcher.group(1).trim());
        if (!fnRef.isFunction()) {
            return Optional.empty();
        }
        DataRecord input;
        if (matcher.group(2) != null && !matcher.group(2).isBlank()) {
            PlatformRef inputRef = PlatformRefParser.parseVariableSource(matcher.group(2).trim());
            input = BindingSourceHelper.readSourceRecord(object, inputRef.name())
                    .orElseGet(() -> DataRecord.empty(
                            object.getVariable(inputRef.name())
                                    .map(v -> v.schema())
                                    .orElse(com.ispf.core.model.DataSchema.builder("voidInput").build())
                    ));
            if (!inputRef.isCurrentObject()) {
                Object remote = context.readRemoteField(inputRef.object(), inputRef.name(), inputRef.field()).orElse(null);
                if (remote != null && input.rowCount() > 0) {
                    var row = new java.util.LinkedHashMap<>(input.firstRow());
                    row.put(inputRef.field(), remote);
                    input = DataRecord.single(input.schema(), row);
                }
            }
        } else {
            input = DataRecord.empty(com.ispf.core.model.DataSchema.builder("voidInput").build());
        }
        String fnPath = fnRef.isCurrentObject() ? object.path() : fnRef.object();
        return context.invokeFunction(fnPath, fnRef.name(), input)
                .flatMap(output -> BindingResultHelper.mapFunctionOutput(object, targetVariable, output));
    }
}
