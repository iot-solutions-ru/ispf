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
import java.util.Locale;

public final class SumEvaluator extends WindowedHistorianEvaluator {

    @Override
    public String helper() {
        return "sum";
    }

    @Override
    public AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        AnalyticsSourceRef source = tag.primarySource();
        Duration window = parseWindow(tag.windowBucket());
        Instant to = options.asOfOrNow();
        Instant from = to.minus(window);
        List<HistorianPort.HistorianBucket> buckets = historian.aggregate(
                source.path(),
                source.variable(),
                source.field(),
                from,
                to,
                tag.windowBucket(),
                48
        );
        if (buckets.isEmpty()) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "No buckets for sum");
        }
        double total = 0.0;
        boolean any = false;
        for (HistorianPort.HistorianBucket bucket : buckets) {
            if (bucket.avg() != null && !bucket.avg().isNaN()) {
                total += bucket.avg();
                any = true;
            }
        }
        if (!any) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "No buckets for sum");
        }
        return singleOutput(tag, total, "No sum data");
    }

    private static Duration parseWindow(String bucketSpec) {
        if (bucketSpec == null || bucketSpec.isBlank()) {
            throw new IllegalArgumentException("Bucket is required");
        }
        return switch (bucketSpec.trim().toLowerCase(Locale.ROOT)) {
            case "1m" -> Duration.ofMinutes(1);
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "8h" -> Duration.ofHours(8);
            case "1d" -> Duration.ofDays(1);
            default -> throw new IllegalArgumentException("Unsupported bucket: " + bucketSpec);
        };
    }
}
