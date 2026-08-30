package com.ispf.server.platform.analytics;

import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.analytics.engine.LiveVariablePort;
import com.ispf.core.object.PlatformObject;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.expression.ExpressionFormalVerifier;
import com.ispf.expression.FormalVerificationReport;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.engine.HistorianCelPreprocessor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ad-hoc analytics CEL-over-historian evaluation API (BL-211).
 */
@Service
public class AnalyticsExpressionService {

    private static final Pattern HISTORIAN_HELPER = Pattern.compile(
            "(avg|min|max|last|sum|live)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

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
        objectManager.require(HistorianTagPaths.objectPath(objectPath));
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
            FormalVerificationReport verification = formalVerifyAnalytics(expression);
            if (verification.blocksConditionApply()) {
                return new ValidateResult(
                        false,
                        expanded,
                        sources,
                        List.of("Formal verification rejected condition: " + String.join("; ", verification.findings())),
                        verification
                );
            }
            return new ValidateResult(true, expanded, sources, List.of(), verification);
        } catch (ExpressionException | IllegalArgumentException | IllegalStateException ex) {
            return new ValidateResult(false, null, sources, List.of(ex.getMessage()), null);
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
        String resolvedObjectPath = HistorianTagPaths.objectPath(objectPath);
        PlatformObject node = objectManager.require(resolvedObjectPath);
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

    /**
     * Historian helpers ({@code avg}/{@code live}/…) are expanded to literals before CEL;
     * formal SMT checks on the expanded form would only reflect current samples. Verify the
     * source expression only when it is pure CEL (no historian helpers).
     */
    private static FormalVerificationReport formalVerifyAnalytics(String expression) {
        if (HISTORIAN_HELPER.matcher(expression).find()) {
            return FormalVerificationReport.skipped(
                    "historian helpers not modeled in SMT yet; formal verify runs on pure CEL conditions"
            );
        }
        return ExpressionFormalVerifier.analyze(expression);
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
            List<String> errors,
            FormalVerificationReport verification
    ) {
        public ValidateResult(
                boolean valid,
                String expandedExpression,
                List<String> historianSources,
                List<String> errors
        ) {
            this(valid, expandedExpression, historianSources, errors, null);
        }
    }

    public record EvaluateResult(
            double value,
            String expandedExpression,
            long latencyMs
    ) {
    }
}
