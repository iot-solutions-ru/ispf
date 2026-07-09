package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.util.List;

public final class MaxWindowEvaluator extends WindowedHistorianEvaluator {

    @Override
    public String helper() {
        return "max";
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
                .map(HistorianPort.HistorianBucket::max)
                .filter(v -> v != null)
                .max(Double::compareTo)
                .orElse(null);
        return singleOutput(tag, value, "No max window data");
    }
}
