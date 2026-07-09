package com.ispf.server.platform.analytics;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;
import com.ispf.core.object.PlatformObject;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.engine.HistorianCelPreprocessor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ad-hoc analytics CEL-over-historian evaluation API (BL-211).
 */
@Service
public class AnalyticsExpressionService {

    private final ObjectManager objectManager;
    private final ExpressionEngine expressionEngine;
    private final HistorianPort historianPort;
    private final LiveVariablePort liveVariablePort;

    public AnalyticsExpressionService(
            ObjectManager objectManager,
            ExpressionEngine expressionEngine,
            HistorianPort historianPort,
            LiveVariablePort liveVariablePort
    ) {
        this.objectManager = objectManager;
        this.expressionEngine = expressionEngine;
        this.historianPort = historianPort;
        this.liveVariablePort = liveVariablePort;
    }

    public ValidateResult validate(String expression, String objectPath) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("objectPath is required");
        }
        objectManager.require(objectPath);
        List<String> sources = HistorianCelPreprocessor.extractSources(expression).stream()
                .map(source -> source.path() + "." + source.variable())
                .toList();
        try {
            String expanded = HistorianCelPreprocessor.expand(
                    expression,
                    historianPort,
                    liveVariablePort,
                    Instant.now()
            );
            expressionEngine.validateCelCompile(expanded);
            return new ValidateResult(true, expanded, sources, List.of());
        } catch (ExpressionException | IllegalArgumentException | IllegalStateException ex) {
            return new ValidateResult(false, null, sources, List.of(ex.getMessage()));
        }
    }

    public EvaluateResult evaluate(String expression, String objectPath, Instant asOf) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("objectPath is required");
        }
        long started = System.nanoTime();
        PlatformObject node = objectManager.require(objectPath);
        Instant resolvedAsOf = asOf != null ? asOf : Instant.now();
        String expanded = HistorianCelPreprocessor.expand(
                expression,
                historianPort,
                liveVariablePort,
                resolvedAsOf
        );
        Object raw = expressionEngine.evaluate(expanded, node, Map.of());
        Double value = toDouble(raw);
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new IllegalArgumentException("Expression did not return a finite number");
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000L;
        return new EvaluateResult(value, expanded, latencyMs);
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

    public record ValidateResult(
            boolean valid,
            String expandedExpression,
            List<String> historianSources,
            List<String> errors
    ) {
    }

    public record EvaluateResult(
            double value,
            String expandedExpression,
            long latencyMs
    ) {
    }
}
