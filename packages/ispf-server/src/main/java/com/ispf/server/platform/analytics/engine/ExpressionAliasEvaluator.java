package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;
import org.springframework.stereotype.Component;

/**
 * Alias helper name {@code expression} for {@link CelExpressionEvaluator} (BL-211).
 */
@Component
public class ExpressionAliasEvaluator implements AnalyticsEvaluator {

    private final CelExpressionEvaluator delegate;

    public ExpressionAliasEvaluator(CelExpressionEvaluator delegate) {
        this.delegate = delegate;
    }

    @Override
    public String helper() {
        return "expression";
    }

    @Override
    public AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        return delegate.evaluate(tag, historian, live, options);
    }
}
