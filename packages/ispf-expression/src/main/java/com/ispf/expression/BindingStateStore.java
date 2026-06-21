package com.ispf.expression;

import java.util.Optional;
import java.util.function.DoubleBinaryOperator;

/**
 * Facade for stateful platform bindings; delegates to the active {@link BindingStatePort}.
 */
final class BindingStateStore {

    private static volatile BindingStatePort port = new InMemoryBindingStatePort();

    private BindingStateStore() {
    }

    static void setPort(BindingStatePort bindingStatePort) {
        port = bindingStatePort != null ? bindingStatePort : new InMemoryBindingStatePort();
    }

    static BindingStatePort port() {
        return port;
    }

    static Optional<Double> previousDouble(String key) {
        return port.previousDouble(key);
    }

    static Double putDouble(String key, double value) {
        return port.putDouble(key, value);
    }

    static Optional<Long> previousTimestampMs(String key) {
        return port.previousTimestampMs(key);
    }

    static void putTimestampMs(String key, long timestampMs) {
        port.putTimestampMs(key, timestampMs);
    }

    static Optional<Boolean> previousBoolean(String key) {
        return port.previousBoolean(key);
    }

    static void putBoolean(String key, boolean value) {
        port.putBoolean(key, value);
    }

    static Optional<Double> aggregateTimedWindow(
            String key,
            long timestampMs,
            double value,
            long windowMs,
            DoubleBinaryOperator aggregator
    ) {
        return port.aggregateTimedWindow(key, timestampMs, value, windowMs, aggregator);
    }

    static Optional<Double> averageTimedWindow(String key, long timestampMs, double value, long windowMs) {
        return port.averageTimedWindow(key, timestampMs, value, windowMs);
    }

    static void clearForTests() {
        port.clearForTests();
    }
}
