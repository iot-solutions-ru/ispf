package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code movingMax(sourceVariable, windowSec[, field])}.
 */
public final class MovingMaxBinding implements PlatformBinding {

    static final MovingMaxBinding INSTANCE = new MovingMaxBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "movingMax\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC
                    + ")\\s*(?:,\\s*(" + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private MovingMaxBinding() {
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
        return MovingMinBinding.aggregate(PATTERN, object, targetVariable, expression, Math::max);
    }
}
