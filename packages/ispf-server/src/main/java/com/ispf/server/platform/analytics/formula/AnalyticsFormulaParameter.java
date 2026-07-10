package com.ispf.server.platform.analytics.formula;

/**
 * Parameter placeholder for a Tier B analytics formula.
 */
public record AnalyticsFormulaParameter(
        String name,
        String type,
        boolean required,
        String description,
        String defaultValue
) {
}
