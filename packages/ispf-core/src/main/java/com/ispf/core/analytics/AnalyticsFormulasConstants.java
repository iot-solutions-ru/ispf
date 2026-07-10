package com.ispf.core.analytics;

/**
 * Platform storage for Tier B user-defined analytics formulas (ADR-0042 / BL-214).
 */
public final class AnalyticsFormulasConstants {

    public static final String FORMULAS_VARIABLE = "@analyticsFormulas";
    public static final String PLATFORM_PATH = "root.platform";

    private AnalyticsFormulasConstants() {
    }

    public static boolean isFormulasVariable(String name) {
        return FORMULAS_VARIABLE.equals(name);
    }
}
