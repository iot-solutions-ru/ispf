package com.ispf.expression;

import java.util.Optional;
import java.util.function.DoubleBinaryOperator;

/**
 * Persistence port for stateful platform bindings (per objectPath|targetVariable key).
 */
public interface BindingStatePort {

    Optional<Double> previousDouble(String key);

    Double putDouble(String key, double value);

    Optional<Long> previousTimestampMs(String key);

    void putTimestampMs(String key, long timestampMs);

    Optional<Boolean> previousBoolean(String key);

    void putBoolean(String key, boolean value);

    Optional<Double> aggregateTimedWindow(
            String key,
            long timestampMs,
            double value,
            long windowMs,
            DoubleBinaryOperator aggregator
    );

    Optional<Double> averageTimedWindow(String key, long timestampMs, double value, long windowMs);

    void clearForTests();
}
