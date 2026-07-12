package com.ispf.expression;

import com.ispf.core.object.PlatformObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code sumScan(<pattern>, <ref>[, <filter>])}.
 */
public final class SumScanBinding implements PlatformBinding {

    static final SumScanBinding INSTANCE = new SumScanBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "sumScan\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*(?:,\\s*\"((?:\\\\.|[^\"])*)\"\\s*)?\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private SumScanBinding() {
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
        String fieldName = ScanSpecBuilder.fieldNameFromRef(matcher.group(2));
        String spec = ScanSpecBuilder.build(matcher.group(1), matcher.group(2), matcher.group(3));
        return context.queryScalar(spec, object.path(), "sum", fieldName);
    }
}
