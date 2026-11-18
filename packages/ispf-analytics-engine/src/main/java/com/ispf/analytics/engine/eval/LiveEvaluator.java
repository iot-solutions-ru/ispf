package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;

import java.util.Map;

public final class LiveEvaluator implements AnalyticsEvaluator {

    @Override
    public String helper() {
        return "live";
    }

    @Override
    public AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        AnalyticsSourceRef source = tag.primarySource();
        Double value = live.readNumeric(source.path(), source.variable(), source.field()).orElse(null);
        if (value == null || value.isNaN() || value.isInfinite()) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "No live value");
        }
        return AnalyticsEvaluationResult.ok(
                tag.tagPath(),
                tag.helper(),
                Map.of(tag.outputVariable(), value)
        );
    }
}
