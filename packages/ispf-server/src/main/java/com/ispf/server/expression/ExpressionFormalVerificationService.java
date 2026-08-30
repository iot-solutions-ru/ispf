package com.ispf.server.expression;

import com.ispf.expression.ExpressionException;
import com.ispf.expression.ExpressionFormalVerifier;
import com.ispf.expression.FormalVerificationReport;
import com.ispf.server.config.ExpressionFormalVerificationProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
     * Analytics / historian path: skip SMT when helpers are present (literals would only
     * reflect the current sample, not the template).
     */
    public FormalVerificationReport analyzeAnalyticsSource(String expression) {
        if (expression != null && HISTORIAN_HELPER.matcher(expression).find()) {
            return FormalVerificationReport.skipped(
                    FormalVerificationReport.CODE_SKIPPED_HISTORIAN,
                    "historian helpers not modeled in SMT yet; formal verify runs on pure CEL conditions"
            );
        }
        return analyze(expression);
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
            // Still compile so syntax errors fail loudly.
            com.ispf.expression.BindingExpressionValidator.validateOrThrow(expression);
            return analyze(expression);
        }
        return ExpressionFormalVerifier.requireSafeConditionOrThrow(expression, options());
    }

    /**
     * Validate-path policy: when enforceOnValidate is true and report blocks, callers
     * should set valid=false.
     */
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
