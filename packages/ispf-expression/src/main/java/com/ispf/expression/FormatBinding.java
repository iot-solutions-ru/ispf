package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code format(pattern, sourceVariable[, field])}.
 * <p>
 * Formats the source field with {@link String#format(String, Object...)} (target schema: STRING).
 */
public final class FormatBinding implements PlatformBinding {

    static final FormatBinding INSTANCE = new FormatBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "format\\(\\s*\"([^\"]*)\"\\s*,\\s*(" + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private FormatBinding() {
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
        String pattern = matcher.group(1);
        BindingSourceHelper.SourceField source = BindingSourceHelper.sourceField(
                matcher.group(2),
                matcher.group(3),
                "value"
        );
        return BindingSourceHelper.readField(object, source.sourceVariable(), source.field())
                .map(value -> String.format(Locale.US, pattern, value));
    }
}
