package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code movingMin(sourceVariable, windowSec[, field])}.
 */
public final class MovingMinBinding implements PlatformBinding {

    static final MovingMinBinding INSTANCE = new MovingMinBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "movingMin\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC
                    + ")\\s*(?:,\\s*(" + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private MovingMinBinding() {
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
        return aggregate(PATTERN, object, targetVariable, expression, Math::min);
    }

    static Optional<Object> aggregate(
            Pattern pattern,
            PlatformObject object,
            String targetVariable,
            String expression,
            java.util.function.DoubleBinaryOperator aggregator
    ) {
        Matcher matcher = pattern.matcher(expression.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        BindingSourceHelper.SourceField source = BindingSourceHelper.sourceField(
                matcher.group(1),
                matcher.group(3),
                "value"
        );
        double windowSec = Double.parseDouble(matcher.group(2));
        long windowMs = (long) (windowSec * 1000);

        Optional<Double> currentOpt = BindingSourceHelper.readNumericField(
                object,
                source.sourceVariable(),
                source.field()
        );
        if (currentOpt.isEmpty()) {
            return Optional.empty();
        }
        long timestampMs = object.getVariable(source.sourceVariable())
                .flatMap(Variable::updatedAt)
                .map(Instant::toEpochMilli)
                .orElseGet(System::currentTimeMillis);

        String stateKey = BindingSourceHelper.stateKey(object, targetVariable);
        return BindingStateStore.aggregateTimedWindow(
                stateKey,
                timestampMs,
                currentOpt.get(),
                windowMs,
                aggregator
        ).map(value -> (Object) value);
    }
}
