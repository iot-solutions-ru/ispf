package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code read(<variableRef>)} — reads a variable field via PlatformRef.
 */
public final class ReadRefBinding implements PlatformBinding {

    static final ReadRefBinding INSTANCE = new ReadRefBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "read\\(\\s*(" + BindingSourceHelper.VARIABLE_REF + ")\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    private ReadRefBinding() {
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
        return PlatformRefValueHelper.readVariable(object, ref, context);
    }
}
