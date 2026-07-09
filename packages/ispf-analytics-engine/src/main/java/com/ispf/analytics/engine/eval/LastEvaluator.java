package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class LastEvaluator extends WindowedHistorianEvaluator {

    @Override
    public String helper() {
        return "last";
    }

    @Override
    public AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        AnalyticsSourceRef source = tag.primarySource();
        Instant to = options.asOfOrNow();
        Instant from = to.minus(24, ChronoUnit.HOURS);
        List<HistorianPort.HistorianSample> samples = historian.query(
                source.path(),
                source.variable(),
                source.field(),
                from,
                to,
                1
        );
        Double value = samples.isEmpty()
                ? live.readNumeric(source.path(), source.variable(), source.field()).orElse(null)
                : samples.getLast().value();
        return singleOutput(tag, value, "No last sample");
    }
}
