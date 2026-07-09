package com.ispf.analytics.engine;

/**
 * Built-in analytics helper evaluator (BL-203).
 */
public interface AnalyticsEvaluator {

    String helper();

    default AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live
    ) {
        return evaluate(tag, historian, live, AnalyticsEvaluationOptions.now());
    }

    AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    );
}
