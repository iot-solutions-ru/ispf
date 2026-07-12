package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code write(<variableRef>, <value>)}.
 */
public final class WriteRefBinding implements PlatformBinding {

    static final WriteRefBinding INSTANCE = new WriteRefBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "write\\(\\s*(" + BindingSourceHelper.VARIABLE_REF + ")\\s*,\\s*(" + BindingSourceHelper.NUMERIC + "|"
                    + BindingSourceHelper.QUOTED_STRING + "|" + BindingSourceHelper.IDENT + ")\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    private WriteRefBinding() {
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
        PlatformRef ref = PlatformRefParser.parseVariableSource(BindingSourceHelper.unwrapQuotedRef(matcher.group(1)));
        Object value = parseValue(matcher.group(2));
        if (!context.writeRemoteField(ref, value, object.path())) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static Object parseValue(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            if (trimmed.contains(".")) {
                return Double.parseDouble(trimmed);
            }
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return trimmed;
        }
    }
}
