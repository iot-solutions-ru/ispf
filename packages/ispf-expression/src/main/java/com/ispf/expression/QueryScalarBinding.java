package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code queryScalar(<spec>, <aggregate>[, <field>])}.
 */
public final class QueryScalarBinding implements PlatformBinding {

    static final QueryScalarBinding INSTANCE = new QueryScalarBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "queryScalar\\(\\s*('(?:\\\\'|[^'])*'|@/[^,)]+|[^,)]+)\\s*,\\s*\"?([A-Za-z_][A-Za-z0-9_]*)\"?\\s*(?:,\\s*\"?([A-Za-z_][A-Za-z0-9_]*)\"?\\s*)?\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private QueryScalarBinding() {
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
        return ObjectQueryBindingHelper.resolveSpec(matcher.group(1), object, context)
                .flatMap(spec -> context.queryScalar(spec, object.path(), matcher.group(2), matcher.group(3)));
    }
}
