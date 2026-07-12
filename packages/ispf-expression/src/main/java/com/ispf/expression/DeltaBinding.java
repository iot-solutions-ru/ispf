package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code delta(sourceVariable[, field])}.
 * <p>
 * Returns the difference from the previous sample of the source field (no time division, no wrap logic).
 */
public final class DeltaBinding implements PlatformBinding {

    static final DeltaBinding INSTANCE = new DeltaBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "delta\\(\\s*(" + BindingSourceHelper.SOURCE_ARG + ")\\s*(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private DeltaBinding() {
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
                matcher.group(2),
                "value"
        );
        Optional<Double> current = BindingSourceHelper.readNumericSource(
                object,
                source.sourceVariable(),
                source.field(),
                context
        );
        if (current.isEmpty()) {
            return Optional.empty();
        }
        String stateKey = BindingSourceHelper.stateKey(object, targetVariable);
        Double previous = BindingStateStore.putDouble(stateKey, current.get());
        if (previous == null) {
            return Optional.empty();
        }
        return Optional.of(current.get() - previous);
    }
}
