package com.ispf.analytics.engine;

import java.util.Map;

/**
 * Result of evaluating one analytics tag (BL-203).
 */
public record AnalyticsEvaluationResult(
        String tagPath,
        String helper,
        String status,
        Map<String, Double> outputs,
        String message
) {

    public static AnalyticsEvaluationResult ok(String tagPath, String helper, Map<String, Double> outputs) {
        return new AnalyticsEvaluationResult(tagPath, helper, "ok", Map.copyOf(outputs), null);
    }

    public static AnalyticsEvaluationResult skipped(String tagPath, String helper, String message) {
        return new AnalyticsEvaluationResult(tagPath, helper, "skipped", Map.of(), message);
    }

    public static AnalyticsEvaluationResult error(String tagPath, String helper, String message) {
        return new AnalyticsEvaluationResult(tagPath, helper, "error", Map.of(), message);
    }
}
