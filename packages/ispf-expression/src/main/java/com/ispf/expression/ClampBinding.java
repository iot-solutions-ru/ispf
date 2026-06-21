package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code clamp(sourceVariable, min, max[, field])}.
 * <p>
 * Clamps a numeric source field to {@code [min, max]}.
 */
public final class ClampBinding implements PlatformBinding {

    static final ClampBinding INSTANCE = new ClampBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "clamp\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC
                    + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC + ")\\s*(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private ClampBinding() {
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
        double min = Double.parseDouble(matcher.group(2));
        double max = Double.parseDouble(matcher.group(3));

        return BindingSourceHelper.readNumericField(object, source.sourceVariable(), source.field())
                .map(value -> clamp(value, min, max));
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
