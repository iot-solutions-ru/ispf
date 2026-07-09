package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

abstract class WindowedHistorianEvaluator implements AnalyticsEvaluator {

    protected List<HistorianPort.HistorianBucket> loadBuckets(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        AnalyticsSourceRef source = tag.primarySource();
        Instant to = options.asOfOrNow();
        Instant from = to.minus(24, ChronoUnit.HOURS);
        return historian.aggregate(
                source.path(),
                source.variable(),
                source.field(),
                from,
                to,
                tag.windowBucket(),
                48
        );
    }

    protected static Double latestAvg(List<HistorianPort.HistorianBucket> buckets) {
        if (buckets.isEmpty()) {
            return null;
        }
        HistorianPort.HistorianBucket last = buckets.getLast();
        return last.avg();
    }

    protected static AnalyticsEvaluationResult singleOutput(
            AnalyticsTagDefinition tag,
            Double value,
            String skipReason
    ) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), skipReason);
        }
        return AnalyticsEvaluationResult.ok(
                tag.tagPath(),
                tag.helper(),
                Map.of(tag.outputVariable(), value)
        );
    }
}
