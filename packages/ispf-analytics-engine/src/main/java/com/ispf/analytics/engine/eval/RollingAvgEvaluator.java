package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.util.List;

public final class RollingAvgEvaluator extends WindowedHistorianEvaluator {

    @Override
    public String helper() {
        return "avg";
    }

    @Override
    public AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        List<HistorianPort.HistorianBucket> buckets = loadBuckets(tag, historian, live, options);
        Double value = latestAvg(buckets);
        if (value == null) {
            AnalyticsSourceRef source = tag.primarySource();
            value = live.readNumeric(source.path(), source.variable(), source.field()).orElse(null);
        }
        return singleOutput(tag, value, "No historian buckets for avg");
    }
}
