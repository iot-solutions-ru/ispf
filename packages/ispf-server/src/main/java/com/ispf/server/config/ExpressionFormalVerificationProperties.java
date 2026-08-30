package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Product settings for CEL formal verification (ADR-0055).
 */
@ConfigurationProperties(prefix = "ispf.expression.formal-verification")
public class ExpressionFormalVerificationProperties {

    /** Master switch. When false, analyze returns skipped and apply is not blocked by formal checks. */
    private boolean enabled = true;

    /** Soft Z3 solver timeout in seconds. */
    private int timeoutSeconds = 2;

    /** When true, REST/UI/AI apply paths reject unsatisfiable/tautology conditions. */
    private boolean enforceOnApply = true;

    /** When true, validate endpoints mark valid=false on failed formal checks. */
    private boolean enforceOnValidate = true;

    private boolean rejectUnsatisfiable = true;

    private boolean rejectTautology = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public boolean isEnforceOnApply() {
        return enforceOnApply;
    }

    public void setEnforceOnApply(boolean enforceOnApply) {
        this.enforceOnApply = enforceOnApply;
    }

    public boolean isEnforceOnValidate() {
        return enforceOnValidate;
    }

    public void setEnforceOnValidate(boolean enforceOnValidate) {
        this.enforceOnValidate = enforceOnValidate;
    }

    public boolean isRejectUnsatisfiable() {
        return rejectUnsatisfiable;
    }

    public void setRejectUnsatisfiable(boolean rejectUnsatisfiable) {
        this.rejectUnsatisfiable = rejectUnsatisfiable;
    }

    public boolean isRejectTautology() {
        return rejectTautology;
    }

    public void setRejectTautology(boolean rejectTautology) {
        this.rejectTautology = rejectTautology;
    }
}
