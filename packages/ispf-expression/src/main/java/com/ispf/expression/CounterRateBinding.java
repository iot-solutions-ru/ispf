package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code counterRate(sourceVariable[, maxCounter[, field]])}.
 * <p>
 * Derives octets/sec (or units/sec of the source field) from an SNMP-style Counter32 source
 * (IF-MIB ifInOctets / ifOutOctets). Handles 32-bit wrap and skips counter resets.
 */
public final class CounterRateBinding implements PlatformBinding {

    /** IF-MIB Counter32 maximum (RFC 2863). */
    public static final long DEFAULT_COUNTER_MAX = 4_294_967_296L;

    static final CounterRateBinding INSTANCE = new CounterRateBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "counterRate\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*(\\d+)\\s*)?(?:,\\s*("
                    + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private static final ConcurrentHashMap<String, Sample> PREVIOUS = new ConcurrentHashMap<>();

    private CounterRateBinding() {
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
                matcher.group(3),
                "value"
        );
        long maxCounter = matcher.group(2) != null
                ? Long.parseLong(matcher.group(2))
                : DEFAULT_COUNTER_MAX;

        Variable sourceVar = object.getVariable(source.sourceVariable())
                .orElseThrow(() -> new ExpressionException("counterRate source not found: " + source.sourceVariable()));
        Optional<DataRecord> record = sourceVar.value();
        if (record.isEmpty() || record.get().rowCount() == 0) {
            return Optional.empty();
        }
        Map<String, Object> row = record.get().firstRow();
        Object raw = row.get(source.field());
        if (!(raw instanceof Number currentNumber)) {
            return Optional.empty();
        }
        double current = currentNumber.doubleValue();
        Instant updatedAt = sourceVar.updatedAt().orElse(Instant.now());
        long nowMs = updatedAt.toEpochMilli();

        String stateKey = BindingSourceHelper.stateKey(object, targetVariable);
        Sample previous = PREVIOUS.get(stateKey);
        PREVIOUS.put(stateKey, new Sample(nowMs, current));

        if (previous == null) {
            return Optional.empty();
        }
        long dtMs = nowMs - previous.timestampMs();
        if (dtMs < 500) {
            return Optional.empty();
        }
        Double delta = counterDelta(current, previous.value(), maxCounter);
        if (delta == null) {
            return Optional.empty();
        }
        return Optional.of(delta / (dtMs / 1000.0));
    }

    @Override
    public void clearStateForTests() {
        PREVIOUS.clear();
    }

    static Double counterDelta(double current, double previous, long maxCounter) {
        if (current >= previous) {
            return current - previous;
        }
        if (previous > maxCounter * 0.75d) {
            return maxCounter - previous + current;
        }
        return null;
    }

    private record Sample(long timestampMs, double value) {
    }
}
