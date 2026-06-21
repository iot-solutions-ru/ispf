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
public final class CounterRateBinding {

    /** IF-MIB Counter32 maximum (RFC 2863). */
    public static final long DEFAULT_COUNTER_MAX = 4_294_967_296L;

    private static final Pattern PATTERN = Pattern.compile(
            "counterRate\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?:,\\s*(\\d+)\\s*)?(?:,\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*)?\\)"
    );

    private static final ConcurrentHashMap<String, Sample> PREVIOUS = new ConcurrentHashMap<>();

    static void clearStateForTests() {
        PREVIOUS.clear();
    }

    private CounterRateBinding() {
    }

    public static boolean matches(String expression) {
        return expression != null && PATTERN.matcher(expression.trim()).matches();
    }

    public static Optional<Double> evaluate(PlatformObject object, String targetVariable, String expression) {
        Matcher matcher = PATTERN.matcher(expression.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String sourceVariable = matcher.group(1);
        long maxCounter = matcher.group(2) != null
                ? Long.parseLong(matcher.group(2))
                : DEFAULT_COUNTER_MAX;
        String field = matcher.group(3) != null ? matcher.group(3) : "value";

        Variable source = object.getVariable(sourceVariable)
                .orElseThrow(() -> new ExpressionException("counterRate source not found: " + sourceVariable));
        Optional<DataRecord> record = source.value();
        if (record.isEmpty() || record.get().rowCount() == 0) {
            return Optional.empty();
        }
        Map<String, Object> row = record.get().firstRow();
        Object raw = row.get(field);
        if (!(raw instanceof Number currentNumber)) {
            return Optional.empty();
        }
        double current = currentNumber.doubleValue();
        Instant updatedAt = source.updatedAt().orElse(Instant.now());
        long nowMs = updatedAt.toEpochMilli();

        String stateKey = object.path() + "|" + targetVariable;
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
