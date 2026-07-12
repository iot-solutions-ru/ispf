package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code countScan(<pattern>[, <filter>])}.
 */
public final class CountScanBinding implements PlatformBinding {

    static final CountScanBinding INSTANCE = new CountScanBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "countScan\\(\\s*\"([^\"]+)\"\\s*(?:,\\s*\"((?:\\\\.|[^\"])*)\"\\s*)?\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private CountScanBinding() {
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
        String spec = ScanSpecBuilder.build(matcher.group(1), null, matcher.group(2));
        return context.queryScalar(spec, object.path(), "count", null);
    }
}
