package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code rate(sourceVariable[, field])}.
 * <p>
 * Derives units/sec from numeric source field changes (no counter wrap logic).
 */
public final class RateBinding implements PlatformBinding {

    static final RateBinding INSTANCE = new RateBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "rate\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private RateBinding() {
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
        Variable sourceVar = object.getVariable(source.sourceVariable())
                .orElseThrow(() -> new ExpressionException("rate source not found: " + source.sourceVariable()));
        Optional<Double> currentOpt = BindingSourceHelper.readNumericField(
                object,
                source.sourceVariable(),
                source.field()
        );
        if (currentOpt.isEmpty()) {
            return Optional.empty();
        }
        double current = currentOpt.get();
        Instant updatedAt = sourceVar.updatedAt().orElse(Instant.now());
        long nowMs = updatedAt.toEpochMilli();

        String stateKey = BindingSourceHelper.stateKey(object, targetVariable);
        Optional<Double> previousOpt = BindingStateStore.previousDouble(stateKey);
        Optional<Long> previousTsOpt = BindingStateStore.previousTimestampMs(stateKey);
        BindingStateStore.putDouble(stateKey, current);
        BindingStateStore.putTimestampMs(stateKey, nowMs);

        if (previousOpt.isEmpty() || previousTsOpt.isEmpty()) {
            return Optional.empty();
        }
        long dtMs = nowMs - previousTsOpt.get();
        if (dtMs < 500) {
            return Optional.empty();
        }
        return Optional.of((current - previousOpt.get()) / (dtMs / 1000.0));
    }
}
