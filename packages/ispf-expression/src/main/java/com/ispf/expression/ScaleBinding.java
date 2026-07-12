package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.ref.PlatformRef;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code scale(sourceVariable, inMin, inMax, outMin, outMax[, field])}.
 * <p>
 * Linearly maps a numeric source field from {@code [inMin, inMax]} to {@code [outMin, outMax]}.
 */
public final class ScaleBinding implements PlatformBinding {

    static final ScaleBinding INSTANCE = new ScaleBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "scale\\(\\s*(.+?)\\s*,\\s*(" + BindingSourceHelper.NUMERIC
                    + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC
                    + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC + ")\\s*(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private ScaleBinding() {
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
        String sourceArg = matcher.group(1).trim();
        BindingSourceHelper.SourceField source = BindingSourceHelper.sourceField(
                sourceArg,
                matcher.group(6),
                "value"
        );
        double inMin = Double.parseDouble(matcher.group(2));
        double inMax = Double.parseDouble(matcher.group(3));
        double outMin = Double.parseDouble(matcher.group(4));
        double outMax = Double.parseDouble(matcher.group(5));

        PlatformRef ref = PlatformRefValueHelper.parseVariableArg(source.sourceVariable());
        if (matcher.group(6) != null) {
            ref = PlatformRef.variable(ref.object(), ref.name(), matcher.group(6));
        }

        return PlatformRefValueHelper.readNumericVariable(object, ref, context)
                .flatMap(value -> scale(value, inMin, inMax, outMin, outMax));
    }

    static Optional<Double> scale(double value, double inMin, double inMax, double outMin, double outMax) {
        if (inMin == inMax) {
            return Optional.empty();
        }
        double scaled = outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
        return Optional.of(scaled);
    }
}
