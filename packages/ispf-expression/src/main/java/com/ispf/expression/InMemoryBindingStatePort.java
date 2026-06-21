package com.ispf.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleBinaryOperator;

/**
 * In-memory binding state (default for tests and standalone expression module).
 */
public final class InMemoryBindingStatePort implements BindingStatePort {

    public static final int MAX_TIMED_SAMPLES = 256;

    private final ConcurrentHashMap<String, Double> lastDouble = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastTimestampMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> lastBoolean = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TimedSample>> timedSamples = new ConcurrentHashMap<>();

    record TimedSample(long timestampMs, double value) {
    }

    @Override
    public Optional<Double> previousDouble(String key) {
        return Optional.ofNullable(lastDouble.get(key));
    }

    @Override
    public Double putDouble(String key, double value) {
        return lastDouble.put(key, value);
    }

    @Override
    public Optional<Long> previousTimestampMs(String key) {
        return Optional.ofNullable(lastTimestampMs.get(key));
    }

    @Override
    public void putTimestampMs(String key, long timestampMs) {
        lastTimestampMs.put(key, timestampMs);
    }

    @Override
    public Optional<Boolean> previousBoolean(String key) {
        return Optional.ofNullable(lastBoolean.get(key));
    }

    @Override
    public void putBoolean(String key, boolean value) {
        lastBoolean.put(key, value);
    }

    @Override
    public Optional<Double> aggregateTimedWindow(
            String key,
            long timestampMs,
            double value,
            long windowMs,
            DoubleBinaryOperator aggregator
    ) {
        List<TimedSample> samples = timedSamples.computeIfAbsent(key, ignored -> new ArrayList<>());
        synchronized (samples) {
            samples.add(new TimedSample(timestampMs, value));
            trimSamples(samples, timestampMs, windowMs);
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

    @Override
    public Optional<Double> averageTimedWindow(String key, long timestampMs, double value, long windowMs) {
        List<TimedSample> samples = timedSamples.computeIfAbsent(key, ignored -> new ArrayList<>());
        synchronized (samples) {
            samples.add(new TimedSample(timestampMs, value));
            trimSamples(samples, timestampMs, windowMs);
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

    @Override
    public void clearForTests() {
        lastDouble.clear();
        lastTimestampMs.clear();
        lastBoolean.clear();
        timedSamples.clear();
    }

    private static void trimSamples(List<TimedSample> samples, long timestampMs, long windowMs) {
        long cutoff = timestampMs - windowMs;
        samples.removeIf(sample -> sample.timestampMs() < cutoff);
        while (samples.size() > MAX_TIMED_SAMPLES) {
            samples.removeFirst();
        }
    }
}
