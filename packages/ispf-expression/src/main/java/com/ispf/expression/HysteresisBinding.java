package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code hysteresis(sourceVariable, onThreshold, offThreshold[, field])}.
 * <p>
 * Boolean output with separate on/off thresholds to avoid chatter.
 */
public final class HysteresisBinding implements PlatformBinding {

    static final HysteresisBinding INSTANCE = new HysteresisBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "hysteresis\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC
                    + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC + ")\\s*(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private HysteresisBinding() {
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
                matcher.group(4),
                "value"
        );
        double onThreshold = Double.parseDouble(matcher.group(2));
        double offThreshold = Double.parseDouble(matcher.group(3));

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
        Optional<Boolean> previousOpt = BindingStateStore.previousBoolean(stateKey);

        boolean next;
        if (current >= onThreshold) {
            next = true;
        } else if (current <= offThreshold) {
            next = false;
        } else if (previousOpt.isPresent()) {
            next = previousOpt.get();
        } else {
            next = false;
        }
        BindingStateStore.putBoolean(stateKey, next);
        return Optional.of(next);
    }
}
