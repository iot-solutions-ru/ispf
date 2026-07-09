package com.ispf.analytics.engine;

/**
 * Thrown when analytics tag dependencies contain a cycle (BL-203).
 */
public class AnalyticsDagCycleException extends IllegalArgumentException {

    public AnalyticsDagCycleException(String message) {
        super(message);
    }
}
