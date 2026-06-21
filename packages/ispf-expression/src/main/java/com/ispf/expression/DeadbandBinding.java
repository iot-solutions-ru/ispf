package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code deadband(sourceVariable, band[, field])}.
 * <p>
 * Emits only when {@code |current - lastEmitted| >= band}.
 */
public final class DeadbandBinding implements PlatformBinding {

    static final DeadbandBinding INSTANCE = new DeadbandBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "deadband\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC
                    + ")\\s*(?:,\\s*(" + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private DeadbandBinding() {
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
        double band = Double.parseDouble(matcher.group(2));

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
        Optional<Double> lastEmittedOpt = BindingStateStore.previousDouble(stateKey);

        if (lastEmittedOpt.isEmpty()) {
            BindingStateStore.putDouble(stateKey, current);
            return Optional.of(current);
        }
        if (Math.abs(current - lastEmittedOpt.get()) >= band) {
            BindingStateStore.putDouble(stateKey, current);
            return Optional.of(current);
        }
        return Optional.empty();
    }
}
