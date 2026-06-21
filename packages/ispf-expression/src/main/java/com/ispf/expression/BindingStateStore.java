package com.ispf.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleBinaryOperator;

/**
 * In-memory state for stateful platform bindings (per JVM, keyed by objectPath|targetVariable).
 */
final class BindingStateStore {

    private static final ConcurrentHashMap<String, Double> LAST_DOUBLE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> LAST_TIMESTAMP_MS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> LAST_BOOLEAN = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<TimedSample>> TIMED_SAMPLES = new ConcurrentHashMap<>();

    private BindingStateStore() {
    }

    record TimedSample(long timestampMs, double value) {
    }

    static Optional<Double> previousDouble(String key) {
        return Optional.ofNullable(LAST_DOUBLE.get(key));
    }

    static Double putDouble(String key, double value) {
        return LAST_DOUBLE.put(key, value);
    }

    static Optional<Long> previousTimestampMs(String key) {
        return Optional.ofNullable(LAST_TIMESTAMP_MS.get(key));
    }

    static void putTimestampMs(String key, long timestampMs) {
        LAST_TIMESTAMP_MS.put(key, timestampMs);
    }

    static Optional<Boolean> previousBoolean(String key) {
        return Optional.ofNullable(LAST_BOOLEAN.get(key));
    }

    static void putBoolean(String key, boolean value) {
        LAST_BOOLEAN.put(key, value);
    }

    static Optional<Double> aggregateTimedWindow(
            String key,
            long timestampMs,
            double value,
            long windowMs,
            DoubleBinaryOperator aggregator
    ) {
        List<TimedSample> samples = TIMED_SAMPLES.computeIfAbsent(key, ignored -> new ArrayList<>());
        synchronized (samples) {
            samples.add(new TimedSample(timestampMs, value));
            long cutoff = timestampMs - windowMs;
            samples.removeIf(sample -> sample.timestampMs() < cutoff);
            if (samples.isEmpty()) {
                return Optional.empty();
            }
            double result = samples.getFirst().value();
            for (int i = 1; i < samples.size(); i++) {
                result = aggregator.applyAsDouble(result, samples.get(i).value());
            }
            return Optional.of(result);
        }
    }

    static Optional<Double> averageTimedWindow(String key, long timestampMs, double value, long windowMs) {
        List<TimedSample> samples = TIMED_SAMPLES.computeIfAbsent(key, ignored -> new ArrayList<>());
        synchronized (samples) {
            samples.add(new TimedSample(timestampMs, value));
            long cutoff = timestampMs - windowMs;
            samples.removeIf(sample -> sample.timestampMs() < cutoff);
            if (samples.isEmpty()) {
                return Optional.empty();
            }
            double sum = 0;
            for (TimedSample sample : samples) {
                sum += sample.value();
            }
            return Optional.of(sum / samples.size());
        }
    }

    static void clearForTests() {
        LAST_DOUBLE.clear();
        LAST_TIMESTAMP_MS.clear();
        LAST_BOOLEAN.clear();
        TIMED_SAMPLES.clear();
    }
}
