package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class RollingAvgEvaluator extends WindowedHistorianEvaluator {

    /** Live fill is only for near-now ticks (DAG bootstrap); never for historical/future asOf probes. */
    private static final Duration LIVE_FALLBACK_SKEW = Duration.ofMinutes(1);

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
        if (value == null && allowLiveFallback(options)) {
            AnalyticsSourceRef source = tag.primarySource();
            value = live.readNumeric(source.path(), source.variable(), source.field()).orElse(null);
        }
        return singleOutput(tag, value, "No historian buckets for avg");
    }

    static boolean allowLiveFallback(AnalyticsEvaluationOptions options) {
        Instant asOf = options.asOf();
        if (asOf == null) {
            return true;
        }
        return Duration.between(asOf, Instant.now()).abs().compareTo(LIVE_FALLBACK_SKEW) <= 0;
    }
}
