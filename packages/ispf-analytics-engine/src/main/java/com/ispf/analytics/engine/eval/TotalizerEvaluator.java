package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.util.List;

public final class TotalizerEvaluator extends WindowedHistorianEvaluator {

    @Override
    public String helper() {
        return "totalizer";
    }

    @Override
    public AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        List<HistorianPort.HistorianBucket> buckets = loadBuckets(tag, historian, live, options);
        if (buckets.isEmpty()) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "No buckets for totalizer");
        }
        double total = 0.0;
        for (HistorianPort.HistorianBucket bucket : buckets) {
            if (bucket.avg() != null) {
                total += bucket.avg() * Math.max(bucket.count(), 1);
            }
        }
        return singleOutput(tag, total, "No totalizer data");
    }
}
