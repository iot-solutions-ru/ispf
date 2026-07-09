package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * OEE composite evaluator (ADR-0041).
 */
@Component
public class OeeEvaluator implements AnalyticsEvaluator {

    @Override
    public String helper() {
        return "oee";
    }

    @Override
    public AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        if (tag.sources().size() < 3) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), helper(), "OEE requires three source variables");
        }
        Instant to = options.asOfOrNow();
        Instant from = to.minus(24, ChronoUnit.HOURS);
        AnalyticsSourceRef availability = tag.sources().get(0);
        AnalyticsSourceRef performance = tag.sources().get(1);
        AnalyticsSourceRef quality = tag.sources().get(2);

        Double availabilityPct = latestAvg(historian.aggregate(
                availability.path(), availability.variable(), availability.field(), from, to, tag.windowBucket(), 48));
        Double performancePct = latestAvg(historian.aggregate(
                performance.path(), performance.variable(), performance.field(), from, to, tag.windowBucket(), 48));
        Double qualityPct = latestAvg(historian.aggregate(
                quality.path(), quality.variable(), quality.field(), from, to, tag.windowBucket(), 48));

        if (availabilityPct == null || performancePct == null || qualityPct == null) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), helper(), "No historian buckets for OEE");
        }

        double oeePct = (availabilityPct / 100.0) * (performancePct / 100.0) * (qualityPct / 100.0) * 100.0;
        return AnalyticsEvaluationResult.ok(tag.tagPath(), helper(), Map.of(tag.outputVariable(), oeePct));
    }

    private static Double latestAvg(List<HistorianPort.HistorianBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return null;
        }
        return buckets.getLast().avg();
    }
}
