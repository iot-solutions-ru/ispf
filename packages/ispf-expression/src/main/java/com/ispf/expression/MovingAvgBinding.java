package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code movingAvg(sourceVariable, windowSec[, field])}.
 */
public final class MovingAvgBinding implements PlatformBinding {

    static final MovingAvgBinding INSTANCE = new MovingAvgBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "movingAvg\\(\\s*(" + BindingSourceHelper.SOURCE_ARG + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC
                    + ")\\s*(?:,\\s*(" + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private MovingAvgBinding() {
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
        BindingSourceHelper.SourceField source = BindingSourceHelper.sourceField(
                matcher.group(1).trim(),
                matcher.group(3),
                "value"
        );
        double windowSec = Double.parseDouble(matcher.group(2));
        long windowMs = (long) (windowSec * 1000);

        var ref = BindingSourceHelper.resolveVariableSource(source.sourceVariable(), source.field());
        Optional<Double> currentOpt = PlatformRefValueHelper.readNumericVariable(object, ref, context);
        if (currentOpt.isEmpty()) {
            return Optional.empty();
        }
        long timestampMs = Instant.now().toEpochMilli();
        if (ref.isCurrentObject() || ref.object().equals(object.path())) {
            timestampMs = object.getVariable(ref.name())
                    .flatMap(Variable::updatedAt)
                    .map(Instant::toEpochMilli)
                    .orElse(timestampMs);
        }

        String stateKey = BindingSourceHelper.stateKey(object, targetVariable);
        return BindingStateStore.averageTimedWindow(stateKey, timestampMs, currentOpt.get(), windowMs)
                .map(value -> (Object) value);
    }
}
