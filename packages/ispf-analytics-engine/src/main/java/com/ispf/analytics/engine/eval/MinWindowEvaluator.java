package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.util.List;

public final class MinWindowEvaluator extends WindowedHistorianEvaluator {

    @Override
    public String helper() {
        return "min";
    }

    @Override
    public AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        List<HistorianPort.HistorianBucket> buckets = loadBuckets(tag, historian, live, options);
        Double value = buckets.stream()
                .map(HistorianPort.HistorianBucket::min)
                .filter(v -> v != null)
                .min(Double::compareTo)
                .orElse(null);
        return singleOutput(tag, value, "No min window data");
    }
}
