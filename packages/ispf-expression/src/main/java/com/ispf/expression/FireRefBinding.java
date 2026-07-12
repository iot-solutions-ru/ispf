package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code fire(<eventRef>)} — side-effect binding returning true when dispatched.
 */
public final class FireRefBinding implements PlatformBinding {

    static final FireRefBinding INSTANCE = new FireRefBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "fire\\(\\s*([^)]+)\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    private FireRefBinding() {
    }

    @Override
    public boolean matches(String expression) {
        if (expression == null) {
            return false;
        }
        String trimmed = expression.trim();
        if (!trimmed.toLowerCase().startsWith("fire(")) {
            return false;
        }
        Matcher matcher = PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return false;
        }
        return PlatformRefParser.parseOptional(matcher.group(1).trim())
                .map(PlatformRef::isEvent)
                .orElse(false);
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
        PlatformRef eventRef = PlatformRefParser.parse(matcher.group(1).trim());
        if (!eventRef.isEvent()) {
            return Optional.empty();
        }
        return context.fireEvent(
                eventRef.isCurrentObject() ? object.path() : eventRef.object(),
                eventRef.name()
        ).map(ignored -> Boolean.TRUE);
    }
}
