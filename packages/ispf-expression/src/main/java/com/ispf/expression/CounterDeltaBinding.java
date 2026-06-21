package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code counterDelta(sourceVariable[, maxCounter[, field]])}.
 * <p>
 * Counter increment with wrap handling (like counterRate) but without time division.
 */
public final class CounterDeltaBinding implements PlatformBinding {

    static final CounterDeltaBinding INSTANCE = new CounterDeltaBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "counterDelta\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*(\\d+)\\s*)?(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private CounterDeltaBinding() {
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
                matcher.group(1),
                matcher.group(3),
                "value"
        );
        long maxCounter = matcher.group(2) != null
                ? Long.parseLong(matcher.group(2))
                : CounterRateBinding.DEFAULT_COUNTER_MAX;

        Optional<Double> currentOpt = BindingSourceHelper.readNumericField(
                object,
                source.sourceVariable(),
                source.field()
        );
        if (currentOpt.isEmpty()) {
            return Optional.empty();
        }
        double current = currentOpt.get();
        String stateKey = BindingSourceHelper.stateKey(object, targetVariable);
        Optional<Double> previousOpt = BindingStateStore.previousDouble(stateKey);
        BindingStateStore.putDouble(stateKey, current);

        if (previousOpt.isEmpty()) {
            return Optional.empty();
        }
        Double delta = CounterRateBinding.counterDelta(current, previousOpt.get(), maxCounter);
        if (delta == null) {
            return Optional.empty();
        }
        return Optional.of(delta);
    }
}
