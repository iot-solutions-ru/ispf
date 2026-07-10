package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.util.List;

public final class EnergyDeltaEvaluator extends WindowedHistorianEvaluator {

    @Override
    public String helper() {
        return "energyDelta";
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
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "Need at least two buckets for energyDelta");
        }

        Double firstAvg = buckets.getFirst().avg();
        Double lastAvg = buckets.getLast().avg();
        if (firstAvg == null || lastAvg == null) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "Bucket average is missing");
        }

        return singleOutput(tag, lastAvg - firstAvg, "No energy delta data");
    }
}
