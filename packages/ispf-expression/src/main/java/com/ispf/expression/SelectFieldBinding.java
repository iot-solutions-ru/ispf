package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code selectField(sourceVariable[, field])}.
 * <p>
 * Returns the named field from the source variable's current value (default field: {@code value}).
 */
public final class SelectFieldBinding implements PlatformBinding {

    static final SelectFieldBinding INSTANCE = new SelectFieldBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "selectField\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private SelectFieldBinding() {
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
                matcher.group(2),
                "value"
        );
        return BindingSourceHelper.readField(object, source.sourceVariable(), source.field());
    }
}
