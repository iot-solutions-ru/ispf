package com.ispf.server.platform.analytics.formula;

/**
 * Result of updating a Tier B formula, including dependent binding-rule rebind count.
 */
public record AnalyticsFormulaUpdateResponse(
        AnalyticsFormula formula,
        int reboundRules
) {
}
