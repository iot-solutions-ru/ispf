package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;
import com.ispf.core.object.PlatformObject;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Evaluates analytics tags with helper {@code cel} / {@code expression} (BL-211).
 */
@Component
public class CelExpressionEvaluator implements AnalyticsEvaluator {

    private final ObjectManager objectManager;
    private final ExpressionEngine expressionEngine;

    public CelExpressionEvaluator(ObjectManager objectManager, ExpressionEngine expressionEngine) {
        this.objectManager = objectManager;
        this.expressionEngine = expressionEngine;
    }

    @Override
    public String helper() {
        return "cel";
    }

    @Override
    public AnalyticsEvaluationResult evaluate(
            AnalyticsTagDefinition tag,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        String expression = tag.expressionOrEmpty();
        if (expression.isBlank()) {
            return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "Missing analyticsExpression");
        }
        try {
            PlatformObject node = objectManager.require(tag.objectPath());
            String expanded = HistorianCelPreprocessor.expand(
                    expression,
                    historian,
                    live,
                    options.asOfOrNow()
            );
            Object raw = expressionEngine.evaluate(expanded, node, Map.of());
            Double value = toDouble(raw);
            if (value == null || value.isNaN() || value.isInfinite()) {
                return AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "Expression did not return a number");
            }
            return AnalyticsEvaluationResult.ok(
                    tag.tagPath(),
                    tag.helper(),
                    Map.of(tag.outputVariable(), value)
            );
        } catch (ExpressionException | IllegalArgumentException | IllegalStateException ex) {
            return AnalyticsEvaluationResult.error(tag.tagPath(), tag.helper(), ex.getMessage());
        }
    }

    private static Double toDouble(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
