package com.ispf.analytics.engine;

import java.util.Optional;

/**
 * Live variable read port for chained derived tags (BL-203).
 */
public interface LiveVariablePort {

    Optional<Double> readNumeric(String objectPath, String variableName, String fieldName);

    default void writeNumeric(String objectPath, String variableName, String fieldName, double value) {
    }
}
