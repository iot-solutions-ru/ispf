package com.ispf.server.expression;

import com.ispf.expression.ExpressionException;
import com.ispf.expression.ExpressionFormalVerifier;
import com.ispf.expression.FormalVerificationReport;
import com.ispf.expression.HistorianCelFormalRewrite;
import com.ispf.server.config.ExpressionFormalVerificationProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Product CEL formal verification gate (ADR-0055).
 *
 * <p>Wraps {@link ExpressionFormalVerifier} with runtime settings for enablement,
 * timeouts, and enforce-on-apply / enforce-on-validate policy.
 */
@Service
public class ExpressionFormalVerificationService {

    private static final Pattern HISTORIAN_HELPER = Pattern.compile(
            "(avg|min|max|last|sum|live)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private final ExpressionFormalVerificationProperties properties;

    public ExpressionFormalVerificationService(ExpressionFormalVerificationProperties properties) {
        this.properties = properties;
    }

    public FormalVerificationReport analyze(String expression) {
        return ExpressionFormalVerifier.analyze(expression, options());
    }

    /**
     * Analytics / historian path: rewrite {@code avg}/{@code live}/… calls to correlated
     * {@code self.__histN} placeholders so boolean templates can be SMT-checked without
     * expanding to the current sample literals.
     */
    public FormalVerificationReport analyzeAnalyticsSource(String expression) {
        if (expression == null || expression.isBlank()) {
            return FormalVerificationReport.skipped(
                    FormalVerificationReport.CODE_SKIPPED_EMPTY,
                    "empty expression"
            );
        }
        if (!HISTORIAN_HELPER.matcher(expression).find()) {
            return analyze(expression);
        }
        HistorianCelFormalRewrite.Result rewritten = HistorianCelFormalRewrite.rewrite(expression);
        FormalVerificationReport report = ExpressionFormalVerifier.analyze(rewritten.rewritten(), options());
        if (!rewritten.rewrittenAny()) {
            return report;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        if (report.details() != null) {
            details.putAll(report.details());
        }
        details.put("historianFormalRewrite", rewritten.rewritten());
        details.put("historianPlaceholders", rewritten.placeholders());
        List<String> findings = new ArrayList<>(report.findings());
        findings.add(0, "historian helpers rewritten to placeholders for formal verify: " + rewritten.rewritten());
        return new FormalVerificationReport(
                report.status(),
                report.engine(),
                report.satisfiable(),
                report.alwaysTrue(),
                report.equivalent(),
                report.codes(),
                List.copyOf(findings),
                Map.copyOf(details)
        );
    }

    public FormalVerificationReport verifyEquivalence(String left, String right) {
        return ExpressionFormalVerifier.verifyEquivalence(left, right, options());
    }

    /**
     * Compile + formal verify for apply paths. Honours {@code enforceOnApply}:
     * when enforcement is off, still returns the report but never throws on formal failure.
     */
    public FormalVerificationReport requireSafeConditionForApply(String expression) {
        if (expression == null || expression.isBlank()) {
            return FormalVerificationReport.skipped(
                    FormalVerificationReport.CODE_SKIPPED_EMPTY,
                    "empty expression"
            );
        }
        if (!properties.isEnabled() || !properties.isEnforceOnApply()) {
            com.ispf.expression.BindingExpressionValidator.validateOrThrow(expression);
            return analyze(expression);
        }
        return ExpressionFormalVerifier.requireSafeConditionOrThrow(expression, options());
    }

    public boolean shouldBlockOnValidate(FormalVerificationReport report) {
        return properties.isEnabled()
                && properties.isEnforceOnValidate()
                && report != null
                && report.blocksConditionApply();
    }

    public boolean shouldBlockOnApply(FormalVerificationReport report) {
        return properties.isEnabled()
                && properties.isEnforceOnApply()
                && report != null
                && report.blocksConditionApply();
    }

    public ExpressionFormalVerificationProperties properties() {
        return properties;
    }

    public ExpressionFormalVerifier.Options options() {
        return new ExpressionFormalVerifier.Options(
                properties.isEnabled(),
                Duration.ofSeconds(properties.getTimeoutSeconds()),
                properties.isRejectUnsatisfiable(),
                properties.isRejectTautology()
        );
    }

    public void rejectIfBlocked(FormalVerificationReport report) {
        if (shouldBlockOnApply(report)) {
            throw new ExpressionException(
                    "Formal verification rejected condition: " + String.join("; ", report.findings())
            );
        }
    }
}
