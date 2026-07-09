package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.util.List;

public final class RateOfChangeEvaluator extends WindowedHistorianEvaluator {

    @Override
    public String helper() {
        return "rateOfChange";
    }

    @Override
    public AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        List<HistorianPort.HistorianBucket> buckets = loadBuckets(tag, historian, live, options);
        if (buckets.size() < 2) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "Need at least two buckets");
        }
        HistorianPort.HistorianBucket first = buckets.getFirst();
        HistorianPort.HistorianBucket last = buckets.getLast();
        double firstAvg = first.avg() != null ? first.avg() : 0.0;
        double lastAvg = last.avg() != null ? last.avg() : 0.0;
        return singleOutput(tag, lastAvg - firstAvg, "No rate-of-change data");
    }
}
