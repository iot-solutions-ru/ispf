package com.ispf.plugin.blueprint;

/**
 * Variables the blueprint engine writes onto a catalog node.
 * They mirror the definition and are not part of the applied payload.
 */
public final class BlueprintCatalogMetadata {

    public static final String BLUEPRINT_TYPE = "blueprintType";
    public static final String SUITABILITY_EXPRESSION = "suitabilityExpression";

    private BlueprintCatalogMetadata() {
    }

    public static boolean isCatalogVariable(String name) {
        return BLUEPRINT_TYPE.equals(name) || SUITABILITY_EXPRESSION.equals(name);
    }
}
