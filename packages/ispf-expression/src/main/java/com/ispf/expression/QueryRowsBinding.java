package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code queryRows(<spec>)} / {@code executeQuery(<spec>)}.
 */
public final class QueryRowsBinding implements PlatformBinding {

    static final QueryRowsBinding INSTANCE = new QueryRowsBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "(?:queryRows|executeQuery)\\(\\s*('(?:\\\\'|[^'])*'|@/[^)]+|[^)]+)\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private QueryRowsBinding() {
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
                .flatMap(spec -> context.queryRows(spec, object.path()))
                .map(OqRowsJson::encode);
    }
}
