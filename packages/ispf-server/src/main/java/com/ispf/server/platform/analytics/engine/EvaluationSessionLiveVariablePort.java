package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.LiveVariablePort;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-evaluation live variable overlay for chained derived tags (BL-204).
 */
final class EvaluationSessionLiveVariablePort implements LiveVariablePort {

    private final LiveVariablePort delegate;
    private final Map<String, Double> session = new ConcurrentHashMap<>();

    EvaluationSessionLiveVariablePort(LiveVariablePort delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<Double> readNumeric(String objectPath, String variableName, String fieldName) {
        Double cached = session.get(key(objectPath, variableName));
        if (cached != null) {
            return Optional.of(cached);
        }
        return delegate.readNumeric(objectPath, variableName, fieldName);
    }

    @Override
    public void writeNumeric(String objectPath, String variableName, String fieldName, double value) {
        session.put(key(objectPath, variableName), value);
    }

    private static String key(String objectPath, String variableName) {
        return objectPath + "\0" + variableName;
    }
}
