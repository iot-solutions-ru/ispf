package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.util.List;

/**
 * Percent change between first and last bucket average in the historian window.
 * Example: {@code percentChange(root.devices.tank01.level, 1h)} → {@code 12.5} means +12.5%.
 */
public final class PercentChangeEvaluator extends WindowedHistorianEvaluator {

    @Override
    public String helper() {
        return "percentChange";
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
            return AnalyticsEvaluationResult.skipped(
                    tag.tagPath(),
                    tag.helper(),
                    "Need at least two buckets for percentChange"
            );
        }

        Double firstAvg = buckets.getFirst().avg();
        Double lastAvg = buckets.getLast().avg();
        if (firstAvg == null || lastAvg == null) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "Bucket average is missing");
        }
        if (firstAvg == 0.0) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "First bucket average is zero");
        }

        double percent = (lastAvg - firstAvg) / Math.abs(firstAvg) * 100.0;
        return singleOutput(tag, percent, "No percent change data");
    }
}
