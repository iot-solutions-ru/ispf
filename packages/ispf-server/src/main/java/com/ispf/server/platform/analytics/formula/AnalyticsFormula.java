package com.ispf.server.platform.analytics.formula;

import java.util.List;

/**
 * Tier B user-defined analytics formula (ADR-0042 / BL-214).
 */
public record AnalyticsFormula(
        String id,
        String displayName,
        String kind,
        String expression,
        List<AnalyticsFormulaParameter> parameters,
        String createdBy,
        int version,
        String scope,
        String appId
) {
    public static final String SCOPE_SITE = "site";
    public static final String SCOPE_APP = "app";

    public static final String KIND_HISTORIAN = "historian";
    public static final String KIND_REACTIVE = "reactive";
}
