package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code refAt("objectPath", variableName[, field])}.
 */
public final class RefAtBinding implements PlatformBinding {

    static final RefAtBinding INSTANCE = new RefAtBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "refAt\\(\\s*" + BindingSourceHelper.QUOTED_STRING + "\\s*,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*(" + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private RefAtBinding() {
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
        String remotePath = matcher.group(1);
        String remoteVariable = matcher.group(2);
        String field = matcher.group(3) != null ? matcher.group(3) : "value";
        return context.readRemoteField(remotePath, remoteVariable, field);
    }
}
