package com.ispf.server.platform.analytics;

import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.analytics.engine.LiveVariablePort;
import com.ispf.core.object.PlatformObject;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.expression.FormalVerificationReport;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.engine.HistorianCelPreprocessor;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.springframework.security.core.Authentication;
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
    private final ExpressionFormalVerificationService formalVerificationService;
    private final VariableMemberAccessService variableMemberAccessService;

    public AnalyticsExpressionService(
            ObjectManager objectManager,
            ExpressionEngine expressionEngine,
            HistorianPort historianPort,
            LiveVariablePort liveVariablePort,
            ExpressionFormalVerificationService formalVerificationService,
            VariableMemberAccessService variableMemberAccessService
    ) {
        this.objectManager = objectManager;
        this.expressionEngine = expressionEngine;
        this.historianPort = historianPort;
        this.liveVariablePort = liveVariablePort;
        this.formalVerificationService = formalVerificationService;
        this.variableMemberAccessService = variableMemberAccessService;
    }

    public ValidateResult validate(String expression, String objectPath, Authentication authentication) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("objectPath is required");
        }
        objectManager.require(HistorianTagPaths.objectPath(objectPath));
        var sourceRefs = HistorianCelPreprocessor.extractSources(expression);
        variableMemberAccessService.requireReadAll(
                authentication,
                sourceRefs.stream()
                        .map(source -> new VariableMemberAccessService.VariableRef(
                                source.path(),
                                source.variable()
                        ))
                        .toList()
        );
        List<String> sources = sourceRefs.stream()
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
            FormalVerificationReport verification = formalVerificationService.analyzeAnalyticsSource(expression);
            if (formalVerificationService.shouldBlockOnValidate(verification)) {
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

    public EvaluateResult evaluate(
            String expression,
            String objectPath,
            Instant asOf,
            Authentication authentication
    ) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("objectPath is required");
        }
        long started = System.nanoTime();
        String resolvedObjectPath = HistorianTagPaths.objectPath(objectPath);
        PlatformObject node = objectManager.require(resolvedObjectPath);
        variableMemberAccessService.requireReadAll(
                authentication,
                HistorianCelPreprocessor.extractSources(expression).stream()
                        .map(source -> new VariableMemberAccessService.VariableRef(
                                source.path(),
                                source.variable()
                        ))
                        .toList()
        );
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
